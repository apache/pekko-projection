/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc

import java.util.UUID

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.r2dbc.QuerySettings
import pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.projection.ProjectionBehavior
import pekko.projection.ProjectionId
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.projection.r2dbc.scaladsl.R2dbcHandler
import pekko.projection.r2dbc.scaladsl.R2dbcProjection
import pekko.projection.r2dbc.scaladsl.R2dbcSession
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

object ChangeSliceRangesSpec {

  val config: Config = ConfigFactory
    .parseString("""
    pekko.persistence.r2dbc {
      query {
        backtracking {
          window = 5 seconds
          behind-current-time = 3 seconds
        }
      }
    }
    """)
    .withFallback(TestConfig.config)

  final case class Processed(projectionId: ProjectionId, envelope: EventEnvelope[String])

}

class ChangeSliceRangesSpec
    extends ScalaTestWithActorTestKit(ChangeSliceRangesSpec.config)
    with AnyWordSpecLike
    with TestDbLifecycle
    with TestData
    with LogCapturing {
  import ChangeSliceRangesSpec._
  import EventSourcedEndToEndSpec.Persister

  override def typedSystem: ActorSystem[?] = system

  private val log = LoggerFactory.getLogger(getClass)

  private val querySettings = QuerySettings(system.settings.config.getConfig("pekko.persistence.r2dbc.query"))
  private val projectionSettings = R2dbcProjectionSettings(system)

  private class TestHandler(projectionId: ProjectionId, probe: ActorRef[Processed], delaySlice: Int)
      extends R2dbcHandler[EventEnvelope[String]] {
    private val log = LoggerFactory.getLogger(getClass)

    override def process(session: R2dbcSession, envelope: EventEnvelope[String]): Future[Done] = {
      val slice = persistenceExt.sliceForPersistenceId(envelope.persistenceId)
      log.debugN("{} Processed {}, pid {}, slice {}", projectionId.key, envelope.event, envelope.persistenceId, slice)
      probe ! Processed(projectionId, envelope)
      if (slice == delaySlice)
        pekko.pattern.after(3.second)(Future.successful(Done))
      else
        Future.successful(Done)
    }
  }

  private def startProjections(
      entityType: String,
      projectionName: String,
      nrOfProjections: Int,
      processedProbe: ActorRef[Processed],
      delaySlice: Int = -1): Vector[ActorRef[ProjectionBehavior.Command]] = {
    val sliceRanges = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, nrOfProjections)

    sliceRanges.map { range =>
      val projectionId = ProjectionId(projectionName, s"${range.min}-${range.max}")
      val sourceProvider =
        EventSourcedProvider
          .eventsBySlices[String](system, R2dbcReadJournal.Identifier, entityType, range.min, range.max)
      val projection = R2dbcProjection
        .exactlyOnce(
          projectionId,
          Some(projectionSettings),
          sourceProvider = sourceProvider,
          handler = () => new TestHandler(projectionId, processedProbe.ref, delaySlice))
      spawn(ProjectionBehavior(projection))
    }.toVector
  }

  def persistenceIdForSlice(entityType: String, slice: Int): PersistenceId = {
    @tailrec def loop(n: Int): PersistenceId = {
      val candidate = PersistenceId(entityType, s"p$n")
      if (persistenceExt.sliceForPersistenceId(candidate.id) == slice)
        candidate
      else
        loop(n + 1)
    }
    loop(0)
  }

  private def mkEvent(n: Int): String = f"e$n%05d"

  private def assertEventsProcessed(
      expectedEvents: Vector[String],
      processedProbe: TestProbe[Processed],
      verifyProjectionId: Boolean): Unit = {
    val expectedNumberOfEvents = expectedEvents.size
    var processed = Vector.empty[Processed]

    (1 to expectedNumberOfEvents).foreach { _ =>
      // not using receiveMessages(expectedEvents) for better logging in case of failure
      try {
        processed :+= processedProbe.receiveMessage(15.seconds)
      } catch {
        case e: AssertionError =>
          val missing = expectedEvents.diff(processed.map(_.envelope.event))
          log.error(s"Processed [${processed.size}] events, but expected [$expectedNumberOfEvents]. " +
            s"Missing [${missing.mkString(",")}]. " +
            s"Received [${processed.map(p =>
                s"(${p.envelope.event}, ${p.envelope.persistenceId}, ${p.envelope.sequenceNr})").mkString(", ")}]. ")
          throw e
      }
    }

    if (verifyProjectionId) {
      val byPid = processed.groupBy(_.envelope.persistenceId)
      byPid.foreach {
        case (_, processedByPid) =>
          // all events of a pid must be processed by the same projection instance
          processedByPid.map(_.projectionId).toSet.size shouldBe 1
          // processed events in right order
          processedByPid.map(_.envelope.sequenceNr).toVector shouldBe (1 to processedByPid.size).toVector
      }
    }
  }

  "Changing projection slice ranges" must {

    "support scaling up and down" in {
      val numberOfEntities = 20
      val numberOfEvents = numberOfEntities * 10
      val entityType = nextEntityType()

      val entities = (0 until numberOfEntities).map { n =>
        val persistenceId = PersistenceId(entityType, s"p$n")
        spawn(Persister(persistenceId), s"$entityType-p$n")
      }

      val projectionName = UUID.randomUUID().toString
      val processedProbe = createTestProbe[Processed]()
      var projections = startProjections(entityType, projectionName, nrOfProjections = 4, processedProbe.ref)

      (1 to numberOfEvents).foreach { n =>
        val p = n % numberOfEntities
        entities(p) ! Persister.Persist(mkEvent(n))

        if (n % 10 == 0)
          Thread.sleep(50)
        else if (n % 25 == 0)
          Thread.sleep(1500)

        // stop projections
        if (n == numberOfEvents / 4) {
          val probe = createTestProbe()
          projections.foreach { ref =>
            ref ! ProjectionBehavior.Stop
            probe.expectTerminated(ref)
          }
        }

        // resume projections again but with more nrOfProjections
        if (n == (numberOfEvents / 4) + 20)
          projections = startProjections(entityType, projectionName, nrOfProjections = 8, processedProbe.ref)

        // stop projections
        if (n == numberOfEvents * 3 / 4) {
          val probe = createTestProbe()
          projections.foreach { ref =>
            ref ! ProjectionBehavior.Stop
            probe.expectTerminated(ref)
          }
        }

        // resume projections again but with less nrOfProjections
        if (n == (numberOfEvents * 3 / 4) + 20)
          projections = startProjections(entityType, projectionName, nrOfProjections = 2, processedProbe.ref)
      }

      val expectedEvents = (1 to numberOfEvents).map(mkEvent).toVector
      assertEventsProcessed(expectedEvents, processedProbe, verifyProjectionId = false)

      projections.foreach(_ ! ProjectionBehavior.Stop)
    }

    "support scaling down after long idle" in {
      val numberOfEntities = 32
      val numberOfEvents = numberOfEntities * 20
      val entityType = nextEntityType()

      val entities = (0 until numberOfEntities).map { n =>
        val persistenceId = persistenceIdForSlice(entityType, (1024 / numberOfEntities) * n)
        spawn(Persister(persistenceId), s"$entityType-p$n")
      }

      val projectionName = UUID.randomUUID().toString
      val processedProbe = createTestProbe[Processed]()
      // slice 0 is slow, 0-511 falling behind 512-1023
      var projections =
        startProjections(entityType, projectionName, nrOfProjections = 4, processedProbe.ref, delaySlice = 0)

      val expectedEvents = (1 to numberOfEvents).map(mkEvent).toVector

      (1 to numberOfEvents).foreach { n =>
        val p = n % numberOfEntities
        entities(p) ! Persister.Persist(mkEvent(n))
        if (n == numberOfEvents / 2)
          Thread.sleep(
            (querySettings.backtrackingWindow + querySettings.backtrackingBehindCurrentTime + 1.second).toMillis)
      }

      // stop projections
      val probe = createTestProbe()
      projections.foreach { ref =>
        ref ! ProjectionBehavior.Stop
        probe.expectTerminated(ref, 10.seconds)
      }
      // start again, with less instances
      projections = startProjections(entityType, projectionName, nrOfProjections = 2, processedProbe.ref)

      assertEventsProcessed(expectedEvents, processedProbe, verifyProjectionId = false)

      projections.foreach(_ ! ProjectionBehavior.Stop)
    }

  }

}
