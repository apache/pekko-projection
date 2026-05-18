/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.producer

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.grpc.GrpcClientSettings
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.Http
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.projection.ProjectionBehavior
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.projection.grpc.TestContainerConf
import pekko.projection.grpc.TestData
import pekko.projection.grpc.TestDbLifecycle
import pekko.projection.grpc.consumer.GrpcQuerySettings
import pekko.projection.grpc.consumer.scaladsl.GrpcReadJournal
import pekko.projection.grpc.producer.scaladsl.EventProducer
import pekko.projection.grpc.producer.scaladsl.EventProducer.EventProducerSource
import pekko.projection.grpc.producer.scaladsl.EventProducer.Transformation
import pekko.projection.r2dbc.scaladsl.R2dbcProjection
import pekko.stream.scaladsl.FlowWithContext
import pekko.testkit.SocketUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object ProducerFilterEndToEndSpec {

  val config: Config = ConfigFactory
    .parseString(s"""
    pekko.actor.allow-java-serialization = on
    pekko.http.server.preview.enable-http2 = on
    pekko.persistence.r2dbc.journal.publish-events = false
    pekko.persistence.r2dbc {
      query {
        refresh-interval = 500 millis
        # reducing this to have quicker test, triggers backtracking earlier
        backtracking.behind-current-time = 3 seconds
      }
    }
    pekko.projection.grpc {
      producer {
        query-plugin-id = "pekko.persistence.r2dbc.query"
      }
    }
    pekko.actor.testkit.typed.filter-leeway = 10s
    """)

  private case class Processed(envelope: EventEnvelope[String])

  private val entityType = "ProducerFilterTestEntity"
  object TestEntity {
    case class Command(text: String, replyTo: ActorRef[Done])
    def apply(id: String): Behavior[Command] =
      EventSourcedBehavior[Command, String, Set[String]](
        PersistenceId(entityType, id),
        Set.empty[String],
        {
          case (_, Command(text, replyTo)) =>
            Effect.persist(text).thenRun(_ => replyTo ! Done)
        },
        (state, event) => state + event)
        .withTaggerForState((state, _) =>
          if (state.exists(_.contains("replicate-it"))) Set("replicate-it") else Set.empty)
  }

}

class ProducerFilterEndToEndSpec(testContainerConf: TestContainerConf)
    extends ScalaTestWithActorTestKit(ProducerFilterEndToEndSpec.config.withFallback(testContainerConf.config))
    with AnyWordSpecLike
    with TestDbLifecycle
    with TestData
    with BeforeAndAfterAll
    with LogCapturing {

  def this() = this(new TestContainerConf)

  import ProducerFilterEndToEndSpec._

  override def typedSystem: ActorSystem[_] = testKit.system
  private implicit val ec: ExecutionContext = typedSystem.executionContext

  private val grpcPort: Int = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

  private val streamId = "producer-filter-e2e-test"
  private val projectionId = ProjectionId("producer-filter-e2e-projection", "0-1023")
  private def grpcJournalSource() =
    EventSourcedProvider.eventsBySlices[String](
      system,
      GrpcReadJournal(
        GrpcQuerySettings(streamId),
        GrpcClientSettings
          .connectToServiceAt("127.0.0.1", grpcPort)
          .withTls(false),
        protobufDescriptors = Nil),
      streamId,
      // just the one consumer for now
      0,
      1023)

  private val projectionProbe = createTestProbe[Processed]()

  private def spawnProjection(): ActorRef[ProjectionBehavior.Command] =
    spawn(
      ProjectionBehavior(
        R2dbcProjection.atLeastOnceFlow(
          projectionId,
          settings = None,
          sourceProvider = grpcJournalSource(),
          handler = FlowWithContext[EventEnvelope[String], ProjectionContext].map { envelope =>
            projectionProbe.ref ! Processed(envelope)
            Done
          })))

  "A projection with producer a filter" must {

    "Start an event producer service" in {
      val eps = EventProducerSource(entityType, streamId, Transformation.identity, EventProducerSettings(system))
        .withProducerFilter[String](envelope => envelope.tags.contains("replicate-it"))
      val handler = EventProducer.grpcServiceHandler(eps)

      val bound =
        Http(system)
          .newServerAt("127.0.0.1", grpcPort)
          .bind(ServiceHandler.concatOrNotFound(handler))
          .map(_.addToCoordinatedShutdown(3.seconds))

      bound.futureValue
    }

    "Start a consumer" in {
      spawnProjection()
    }

    "Filter events according to producer filter" in {
      val ackProbe = createTestProbe[Done]()
      val entity1 = testKit.spawn(TestEntity("one"))
      entity1.tell(TestEntity.Command("a", ackProbe.ref))
      ackProbe.receiveMessage()
      entity1.tell(TestEntity.Command("b", ackProbe.ref))
      ackProbe.receiveMessage()

      projectionProbe.expectNoMessage()

      entity1.tell(TestEntity.Command("c-replicate-it", ackProbe.ref))
      ackProbe.receiveMessage()

      val replicatedEvents = projectionProbe.receiveMessages(3, 5.seconds) // behind-current-time + some more time
      replicatedEvents.map(_.envelope.event) shouldBe Seq("a", "b", "c-replicate-it")
      replicatedEvents.head.envelope.tags shouldBe Set.empty
      replicatedEvents.last.envelope.tags shouldBe Set("replicate-it")
    }

  }

}
