/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

// #testKitSpec
package docs.guide

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.query.Offset
import pekko.projection.ProjectionId
import pekko.projection.eventsourced.EventEnvelope
import pekko.stream.scaladsl.Source
// #testKitImports
import org.apache.pekko
import pekko.projection.testkit.scaladsl.ProjectionTestKit
import pekko.projection.testkit.scaladsl.TestProjection
import pekko.projection.testkit.scaladsl.TestSourceProvider
// #testKitImports
import org.scalatest.wordspec.AnyWordSpecLike

object ShoppingCartAppSpec {
  // mock out the Cassandra data layer and simulate recording item count updates
  class MockItemPopularityRepository extends ItemPopularityProjectionRepository {
    var counts: Map[String, Long] = Map.empty

    override def update(itemId: String, delta: Int): Future[Done] = Future.successful {
      counts = counts + (itemId -> (counts.getOrElse(itemId, 0L) + delta))
      Done
    }

    override def getItem(itemId: String): Future[Option[Long]] =
      Future.successful(counts.get(itemId))
  }
}

class ShoppingCartAppSpec extends ScalaTestWithActorTestKit() with AnyWordSpecLike {
  import ShoppingCartAppSpec._

  private val projectionTestKit = ProjectionTestKit(system)

  def createEnvelope(event: ShoppingCartEvents.Event, seqNo: Long, timestamp: Long = 0L) =
    EventEnvelope(Offset.sequence(seqNo), "persistenceId", seqNo, event, timestamp)

  "The ItemPopularityProjectionHandler" should {
    "process item events correctly" in {
      val repo = new MockItemPopularityRepository
      val handler = new ItemPopularityProjectionHandler("tag", system, repo)

      val events = Source(
        List[EventEnvelope[ShoppingCartEvents.Event]](
          createEnvelope(ShoppingCartEvents.ItemAdded("a7098", "bowling shoes", 1), 0L),
          createEnvelope(ShoppingCartEvents.ItemQuantityAdjusted("a7098", "bowling shoes", 2, 1), 1L),
          createEnvelope(ShoppingCartEvents.CheckedOut("a7098", Instant.parse("2020-01-01T12:00:00.00Z")), 2L),
          createEnvelope(ShoppingCartEvents.ItemAdded("0d12d", "pekko t-shirt", 1), 3L),
          createEnvelope(ShoppingCartEvents.ItemAdded("0d12d", "skis", 1), 4L),
          createEnvelope(ShoppingCartEvents.ItemRemoved("0d12d", "skis", 1), 5L),
          createEnvelope(ShoppingCartEvents.CheckedOut("0d12d", Instant.parse("2020-01-01T12:05:00.00Z")), 6L)))

      val projectionId = ProjectionId("name", "key")
      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[ShoppingCartEvents.Event]](events, extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[ShoppingCartEvents.Event]](projectionId, sourceProvider, () => handler)

      projectionTestKit.run(projection) {
        repo.counts shouldBe Map("bowling shoes" -> 2, "pekko t-shirt" -> 1, "skis" -> 0)
      }
    }

    "log item popularity for day every 10 item events" in {
      val repo = new MockItemPopularityRepository
      val handler = new ItemPopularityProjectionHandler("tag", system, repo)

      val events = (0L until 10L).map { i =>
        createEnvelope(ShoppingCartEvents.ItemAdded("a7098", "bowling shoes", 1), i)
      }

      val projectionId = ProjectionId("name", "key")
      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[ShoppingCartEvents.Event]](
          Source(events),
          extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[ShoppingCartEvents.Event]](projectionId, sourceProvider, () => handler)

      LoggingTestKit
        .info("ItemPopularityProjectionHandler(tag) item popularity for 'bowling shoes': [10]")
        .expect {
          projectionTestKit.runWithTestSink(projection) { testSink =>
            testSink.request(events.length)
            testSink.expectNextN(events.length)
          }
        }
    }
  }
}
// #testKitSpec
