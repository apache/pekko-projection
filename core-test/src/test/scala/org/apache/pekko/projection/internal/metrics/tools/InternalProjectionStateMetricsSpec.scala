/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.internal.metrics.tools

import java.util.UUID

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorSystem
import pekko.event.Logging
import pekko.event.LoggingAdapter
import pekko.projection.ProjectionId
import pekko.projection.RunningProjection
import pekko.projection.StatusObserver
import pekko.projection.internal.ExactlyOnce
import pekko.projection.internal.FlowHandlerStrategy
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.HandlerStrategy
import pekko.projection.internal.InternalProjectionState
import pekko.projection.internal.NoopStatusObserver
import pekko.projection.internal.OffsetStrategy
import pekko.projection.internal.ProjectionSettings
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.scaladsl.Handler
import pekko.projection.scaladsl.SourceProvider
import pekko.projection.testkit.internal.TestInMemoryOffsetStoreImpl
import pekko.projection.testkit.scaladsl.TestSourceProvider
import pekko.stream.SharedKillSwitch
import pekko.stream.scaladsl.Source
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfter
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Superclass for the MetricsSpec run directly over [[InternalProjectionState]]. This provides a fake
 * `SourceProvider` and a fake projection implementation (not Slick, not Cassandra,...) so it can live
 * on pekko-projections.core. That implementation has its own offset store and all.
 *
 * MetricsSpecs should create a [[TelemetryTester]] and run the projection using `runInternal` (see existing
 * `XyzMetricsSpec` for more examples.
 */
abstract class InternalProjectionStateMetricsSpec
    extends ScalaTestWithActorTestKit(InternalProjectionStateMetricsSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfter
    with LogCapturing {

  import InternalProjectionStateMetricsSpec._

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val actorSystem: ActorSystem[Nothing] = testKit.system
  implicit val executionContext: ExecutionContext = testKit.system.executionContext
  val zero = scala.concurrent.duration.Duration.Zero

  protected def genRandomProjectionId() =
    ProjectionId(UUID.randomUUID().toString, UUID.randomUUID().toString)

  val maxRetries = 100

  // inspired on ProjectionTestkit's runInternal
  protected def runInternal(
      projectionState: InMemInternalProjectionState[_, _],
      max: FiniteDuration = 3.seconds,
      interval: FiniteDuration = 100.millis)(assertFunction: => Unit): Unit = {

    val probe = testKit.createTestProbe[Nothing]("internal-projection-state-probe")

    val running: RunningProjection =
      projectionState.newRunningInstance()
    try {
      probe.awaitAssert(assertFunction, max.dilated, interval)
    } finally {
      Await.result(running.stop(), max)
    }
  }

}

object InternalProjectionStateMetricsSpec {
  def config: Config =
    ConfigFactory.parseString(s"""
      pekko {
        loglevel = "DEBUG"
      }
      // Recover fast to speed up tests.
      pekko.projection.restart-backoff{
        min-backoff = 30ms
        max-backoff = 50ms
        random-factor = 0.1
      }
      pekko.projection {
        telemetry.implementations = [${classOf[InMemTelemetry].getName}]
      }
      """)
  case class Envelope(id: String, offset: Long, message: String) {
    // some time in the past...
    val creationTimestamp = System.currentTimeMillis() - 1000L
  }

  def sourceProvider(id: String, numberOfEnvelopes: Int): SourceProvider[Long, Envelope] = {
    val chars = "abcdefghijklmnopqrstuvwxyz"
    val envelopes = (1 to numberOfEnvelopes).map { offset =>
      Envelope(id, offset.toLong, chars.charAt((offset - 1) % chars.length).toString)
    }
    TestSourceProvider[Long, Envelope](Source(envelopes), _.offset)
      .withStartSourceFrom((lastProcessedOffset, offset) => offset <= lastProcessedOffset)
  }

  class TelemetryTester(
      offsetStrategy: OffsetStrategy,
      handlerStrategy: HandlerStrategy,
      numberOfEnvelopes: Int = 6,
      statusObserver: StatusObserver[Envelope] = NoopStatusObserver)(
      implicit system: ActorSystem[_],
      projectionId: ProjectionId) {

    private implicit val exCtx: ExecutionContext = system.executionContext
    val entityId = UUID.randomUUID().toString

    private val projectionSettings = ProjectionSettings(system)

    val offsetStore = new TestInMemoryOffsetStoreImpl[Long]()

    val adaptedHandlerStrategy: HandlerStrategy = offsetStrategy match {
      case ExactlyOnce(_) =>
        handlerStrategy match {
          case singleHandlerStrategy: SingleHandlerStrategy[Envelope] @unchecked => {
            val adaptedHandler = () =>
              new Handler[Envelope] {
                override def process(envelope: Envelope): Future[Done] =
                  singleHandlerStrategy.handlerFactory().process(envelope).flatMap {
                    _ =>
                      offsetStore.saveOffset(projectionId, envelope.offset)
                  }
              }
            SingleHandlerStrategy(adaptedHandler)
          }
          case groupedHandlerStrategy: GroupedHandlerStrategy[Envelope] @unchecked => {
            val adaptedHandler = () =>
              new Handler[immutable.Seq[Envelope]] {
                override def process(envelopes: immutable.Seq[Envelope]): Future[Done] =
                  groupedHandlerStrategy.handlerFactory().process(envelopes).flatMap { _ =>
                    offsetStore.saveOffset(projectionId, envelopes.last.offset)
                  }
              }
            GroupedHandlerStrategy(adaptedHandler, groupedHandlerStrategy.afterEnvelopes,
              groupedHandlerStrategy.orAfterDuration)
          }
          case FlowHandlerStrategy(_) => handlerStrategy
        }
      case _ => handlerStrategy
    }

    val projectionState =
      new InMemInternalProjectionState[Long, Envelope](
        projectionId,
        sourceProvider(entityId, numberOfEnvelopes),
        offsetStrategy,
        adaptedHandlerStrategy,
        statusObserver,
        projectionSettings,
        offsetStore)

    lazy val inMemTelemetry = projectionState.getTelemetry().asInstanceOf[InMemTelemetry]

  }

  class InMemInternalProjectionState[Offset, Env](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Env],
      offsetStrategy: OffsetStrategy,
      handlerStrategy: HandlerStrategy,
      statusObserver: StatusObserver[Env],
      settings: ProjectionSettings,
      offsetStore: TestInMemoryOffsetStoreImpl[Offset])(implicit val system: ActorSystem[_])
      extends InternalProjectionState[Offset, Env](
        projectionId,
        sourceProvider,
        offsetStrategy,
        handlerStrategy,
        statusObserver,
        settings) {
    override def logger: LoggingAdapter =
      Logging(system.classicSystem, classOf[InMemInternalProjectionState[Offset, Env]])

    override implicit def executionContext: ExecutionContext = system.executionContext

    override def readPaused(): Future[Boolean] =
      offsetStore.readManagementState(projectionId).map(_.exists(_.paused))

    override def readOffsets(): Future[Option[Offset]] = offsetStore.readOffsets()

    override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] =
      offsetStore.saveOffset(projectionId, offset)

    def newRunningInstance(): RunningProjection =
      new TestRunningProjection(RunningProjection.withBackoff(() => mappedSource(), settings), killSwitch)

    class TestRunningProjection(val source: Source[Done, _], killSwitch: SharedKillSwitch) extends RunningProjection {

      private val futureDone = source.run()

      override def stop(): Future[Done] = {
        killSwitch.shutdown()
        futureDone
      }
    }
  }

}
