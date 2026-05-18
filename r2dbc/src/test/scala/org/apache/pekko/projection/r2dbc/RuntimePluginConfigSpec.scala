/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pekko.projection.r2dbc

import java.util.UUID

import scala.collection.immutable.ListSet
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.Done
import pekko.actor.ExtendedActorSystem
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.adapter._
import pekko.persistence.Persistence
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.r2dbc.ConnectionFactoryProvider
import pekko.persistence.r2dbc.JournalSettings
import pekko.persistence.r2dbc.SnapshotSettings
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.persistence.r2dbc.state.scaladsl.R2dbcDurableStateStore
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.persistence.typed.scaladsl.RetentionCriteria
import pekko.projection.ProjectionBehavior
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.projection.r2dbc.scaladsl.R2dbcHandler
import pekko.projection.r2dbc.scaladsl.R2dbcProjection
import pekko.projection.r2dbc.scaladsl.R2dbcSession
import pekko.projection.scaladsl.Handler
import pekko.stream.scaladsl.FlowWithContext

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Inside
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

object RuntimePluginConfigSpec {

  trait EventSourced {
    import EventSourced._

    def configKey: String
    def database: String

    lazy val unresolvedConfig = ConfigFactory
      .parseString(
        s"""
              $configKey = $${pekko.persistence.r2dbc}
              $configKey = {
                connection-factory {
                  database = "$database"
                }

                journal.$configKey.connection-factory = $${$configKey.connection-factory}
                journal.use-connection-factory = "$configKey.connection-factory"
                query.$configKey.connection-factory = $${$configKey.connection-factory}
                query.use-connection-factory = "$configKey.connection-factory"
                snapshot.$configKey.connection-factory = $${$configKey.connection-factory}
                snapshot.use-connection-factory = "$configKey.connection-factory"
              }
              """
      )

    lazy val config: Config = ConfigFactory.load(unresolvedConfig.withFallback(TestConfig.unresolvedConfig))

    def apply(persistenceId: String): Behavior[Command] =
      EventSourcedBehavior[Command, String, String](
        PersistenceId.ofUniqueId(persistenceId),
        "",
        (state, cmd) =>
          cmd match {
            case Save(text, replyTo) =>
              Effect.persist(text).thenRun(_ => replyTo ! Done)
            case ShowMeWhatYouGot(replyTo) =>
              replyTo ! state
              Effect.none
            case Stop =>
              Effect.stop()
          },
        (state, evt) => Seq(state, evt).filter(_.nonEmpty).mkString("|"))
        .withRetention(RetentionCriteria.snapshotEvery(1, Int.MaxValue))
        .withJournalPluginId(s"$configKey.journal")
        .withJournalPluginConfig(Some(config))
        .withSnapshotPluginId(s"$configKey.snapshot")
        .withSnapshotPluginConfig(Some(config))
  }
  object EventSourced {
    sealed trait Command
    case class Save(text: String, replyTo: ActorRef[Done]) extends Command
    case class ShowMeWhatYouGot(replyTo: ActorRef[String]) extends Command
    case object Stop extends Command
  }
}

class RuntimePluginConfigSpec extends ScalaTestWithActorTestKit(TestConfig.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with LogCapturing
    with Inside {

  import RuntimePluginConfigSpec._

  private lazy val eventSourced1 = new EventSourced {
    override def configKey: String = "plugin1"
    override def database: String = "database1"
  }
  private lazy val eventSourced2 = new EventSourced {
    override def configKey: String = "plugin2"
    override def database: String = "database2"
  }

  override protected def beforeEach(): Unit = {
    super.beforeAll()

    ListSet(eventSourced1, eventSourced2).foreach { eventSourced =>
      val journalConfig = eventSourced.config.getConfig(s"${eventSourced.configKey}.journal")
      val journalSettings: JournalSettings = JournalSettings(journalConfig)

      val snapshotSettings: SnapshotSettings =
        SnapshotSettings(eventSourced.config.getConfig(s"${eventSourced.configKey}.snapshot"))

      // making sure that test harness does not initialize connection factory for the plugin that is being tested
      val connectionFactoryProvider =
        ConnectionFactoryProvider(system)
          .connectionFactoryFor(s"test.${eventSourced.configKey}.connection-factory",
            journalConfig.getConfig(journalSettings.useConnectionFactory).atPath(
              s"test.${eventSourced.configKey}.connection-factory"))

      // this assumes that journal, snapshot store, state and projection use same connection settings
      val r2dbcExecutor: R2dbcExecutor =
        new R2dbcExecutor(
          connectionFactoryProvider,
          LoggerFactory.getLogger(getClass),
          journalSettings.logDbCallsExceeding)(system.executionContext, system)

      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${journalSettings.journalTableWithSchema}")),
        10.seconds)
      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${snapshotSettings.snapshotsTableWithSchema}")),
        10.seconds)

      val r2dbcProjectionSettings = R2dbcProjectionSettings(eventSourced.projectionConfig, system)
      if (r2dbcProjectionSettings.isOffsetTableDefined) {
        Await.result(
          r2dbcExecutor.updateOne("beforeAll delete")(
            _.createStatement(s"delete from ${r2dbcProjectionSettings.offsetTableWithSchema}")),
          10.seconds)
      }
      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${r2dbcProjectionSettings.timestampOffsetTableWithSchema}")),
        10.seconds)
      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${r2dbcProjectionSettings.managementTableWithSchema}")),
        10.seconds)
    }
  }

  private def runTest(
      spawnProjection: EventSourced => ProjectionResult
  ) = {
    val probe = createTestProbe[Any]()

    {
      // one actor in each journal with same id
      val j1 = spawn(eventSourced1("id1"))
      val j2 = spawn(eventSourced2("id1"))
      j1 ! EventSourced.Save("j1m1", probe.ref)
      probe.receiveMessage()
      j2 ! EventSourced.Save("j2m1", probe.ref)
      probe.receiveMessage()
    }

    def assertProjection(eventSourced: EventSourced, expectedEvent: String) = {
      val result = eventSourced.Projection.atLeastOnce()
      val message = result.probe.receiveMessage()
      message.persistenceId shouldBe "id1"
      message.event shouldBe expectedEvent
      result.probe.expectNoMessage()

      result.projectionRef ! ProjectionBehavior.Stop
      probe.expectTerminated(result.projectionRef)
    }

    assertProjection(eventSourced1, "j1m1")
    assertProjection(eventSourced2, "j2m1")
  }

  "Runtime plugin config" should {
    "work for at least once projections of event sourced behaviors" in {
      runTest(_.Projection.atLeastOnce())
    }

    "work for at least once async projections of event sourced behaviors" in {
      runTest(_.Projection.atLeastOnceAsync())
    }

    "work for at least once flow projections of event sourced behaviors" in {
      runTest(_.Projection.atLeastOnceFlow())
    }

    "work for exactly once projections of event sourced behaviors" in {
      runTest(_.Projection.exactlyOnce())
    }
  }

  final case class ProjectionResult(
      projectionRef: ActorRef[ProjectionBehavior.Command],
      probe: TestProbe[EventEnvelope[String]]
  )

  implicit class EventSourcedOps(eventSourced: EventSourced) {

    lazy val projectionConfig: Config = ConfigFactory.load(
      eventSourced.unresolvedConfig
        .withFallback(
          ConfigFactory
            .parseString(
              s"""
              pekko.projection.r2dbc.use-connection-factory = "${eventSourced.configKey}.connection-factory"
              """
            )
        )
        .withFallback(TestConfig.unresolvedConfig)
    )

    object Projection {
      private val probe = createTestProbe[EventEnvelope[String]]()
      private val readJournalPluginIdentifier = s"${eventSourced.configKey}.query"
      private val range = 0 until Persistence(system).numberOfSlices
      private val sourceProvider = EventSourcedProvider.eventsBySlices[String](
        system = system,
        readJournalPluginId = readJournalPluginIdentifier,
        readJournalConfig = eventSourced.config,
        entityType = "",
        minSlice = range.min,
        maxSlice = range.max
      )
      private def generateProjectionId() =
        ProjectionId(s"runtime-plugin-config-spec-${UUID.randomUUID()}", s"${range.min}-${range.max}")

      def atLeastOnce(): ProjectionResult = {
        val projection = R2dbcProjection.atLeastOnce(
          projectionId = generateProjectionId(),
          config = eventSourced.projectionConfig,
          settings = None,
          sourceProvider = sourceProvider,
          handler = () =>
            new R2dbcHandler[EventEnvelope[String]] {
              override def process(session: R2dbcSession, envelope: EventEnvelope[String]): Future[Done] = {
                probe.ref ! envelope
                Future.successful(Done)
              }
            }
        )
        val projectionRef = spawn(ProjectionBehavior(projection))
        ProjectionResult(projectionRef, probe)
      }

      def atLeastOnceAsync(): ProjectionResult = {
        val projection = R2dbcProjection.atLeastOnceAsync(
          projectionId = generateProjectionId(),
          config = eventSourced.projectionConfig,
          settings = None,
          sourceProvider = sourceProvider,
          handler = () =>
            new Handler[EventEnvelope[String]] {
              override def process(envelope: EventEnvelope[String]): Future[Done] = {
                probe.ref ! envelope
                Future.successful(Done)
              }
            }
        )
        val projectionRef = spawn(ProjectionBehavior(projection))
        ProjectionResult(projectionRef, probe)
      }

      def atLeastOnceFlow(): ProjectionResult = {
        val projection = R2dbcProjection.atLeastOnceFlow(
          projectionId = generateProjectionId(),
          config = eventSourced.projectionConfig,
          settings = None,
          sourceProvider = sourceProvider,
          handler = FlowWithContext[EventEnvelope[String], ProjectionContext]
            .mapAsync(1) { envelope =>
              probe.ref ! envelope
              Future.successful(Done)
            }
        )
        val projectionRef = spawn(ProjectionBehavior(projection))
        ProjectionResult(projectionRef, probe)
      }

      def exactlyOnce(): ProjectionResult = {
        val projection = R2dbcProjection.exactlyOnce(
          projectionId = generateProjectionId(),
          config = eventSourced.projectionConfig,
          settings = None,
          sourceProvider = sourceProvider,
          handler = () =>
            new R2dbcHandler[EventEnvelope[String]] {
              override def process(session: R2dbcSession, envelope: EventEnvelope[String]): Future[Done] = {
                probe.ref ! envelope
                Future.successful(Done)
              }
            }
        )
        val projectionRef = spawn(ProjectionBehavior(projection))
        ProjectionResult(projectionRef, probe)
      }
    }
  }
}
