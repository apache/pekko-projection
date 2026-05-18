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

package org.apache.pekko.projection.grpc.replication

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import pekko.cluster.MemberStatus
import pekko.cluster.sharding.typed.javadsl.ClusterSharding
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.grpc.GrpcClientSettings
import pekko.http.javadsl.Http
import pekko.http.javadsl.ServerBinding
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.crdt.LwwTime
import pekko.persistence.typed.javadsl.CommandHandler
import pekko.persistence.typed.javadsl.EventHandler
import pekko.persistence.typed.javadsl.EventSourcedBehavior
import pekko.persistence.typed.javadsl.ReplicationContext
import pekko.projection.grpc.TestContainerConf
import pekko.projection.grpc.TestDbLifecycle
import pekko.projection.grpc.producer.EventProducerSettings
import pekko.projection.grpc.replication
import pekko.projection.grpc.replication.javadsl.Replica
import pekko.projection.grpc.replication.javadsl.ReplicatedBehaviors
import pekko.projection.grpc.replication.javadsl.Replication
import pekko.projection.grpc.replication.javadsl.ReplicationSettings
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.javadsl.R2dbcReplication
import pekko.testkit.SocketUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import java.time.Duration
import scala.jdk.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

object ReplicationJavaDSLIntegrationSpec {

  private def config(dc: ReplicaId): Config =
    ConfigFactory.parseString(s"""
       pekko.actor.provider = cluster
       pekko.actor {
         serialization-bindings {
           "${classOf[replication.ReplicationJavaDSLIntegrationSpec].getName}$$LWWHelloWorld$$Event" = jackson-json
         }
       }
       pekko.http.server.preview.enable-http2 = on
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
        pekko.projection.r2dbc.offset-store {
          timestamp-offset-table = "projection_timestamp_offset_store_${dc.id}"
        }
        pekko.remote.artery.canonical.host = "127.0.0.1"
        pekko.remote.artery.canonical.port = 0
        pekko.actor.testkit.typed {
          filter-leeway = 10s
          system-shutdown-default = 30s
        }
      """)

  private val DCA = ReplicaId("DCA")
  private val DCB = ReplicaId("DCB")
  private val DCC = ReplicaId("DCC")

  object LWWHelloWorld {

    sealed trait Command

    case class Get(replyTo: ActorRef[String]) extends Command

    case class SetGreeting(newGreeting: String, replyTo: ActorRef[Done]) extends Command

    sealed trait Event

    case class GreetingChanged(greeting: String, timestamp: LwwTime) extends Event

    object State {
      val initial = State("Hello world", LwwTime(Long.MinValue, ReplicaId("")))
    }

    case class State(greeting: String, timestamp: LwwTime)

    def create(replicatedBehaviors: ReplicatedBehaviors[Command, Event, State]) =
      replicatedBehaviors.setup { replicationContext => new LWWHelloWorldBehavior(replicationContext) }

    class LWWHelloWorldBehavior(replicationContext: ReplicationContext)
        extends EventSourcedBehavior[Command, Event, State](replicationContext.persistenceId) {
      protected def emptyState: State = State.initial

      protected def commandHandler(): CommandHandler[Command, Event, State] =
        newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(classOf[Get],
            { (state: State, command: Get) =>
              command.replyTo.tell(state.greeting)
              Effect.none()
            })
          .onCommand(
            classOf[SetGreeting],
            { (state: State, command: SetGreeting) =>
              Effect
                .persist(
                  GreetingChanged(
                    greeting = command.newGreeting,
                    timestamp =
                      state.timestamp.increase(replicationContext.currentTimeMillis(), replicationContext.replicaId)))
                .thenReply(command.replyTo, _ => Done)
            })
          .build()

      protected def eventHandler(): EventHandler[State, Event] =
        newEventHandlerBuilder()
          .forAnyState()
          .onEvent(classOf[GreetingChanged],
            { (currentState: State, event: GreetingChanged) =>
              if (event.timestamp.isAfter(currentState.timestamp)) State(event.greeting, event.timestamp)
              else currentState
            })
          .build()
    }
  }
}

// A shorter version of ReplicationIntegrationSpec covering the Java DSL for bootstrapping
class ReplicationJavaDSLIntegrationSpec(testContainerConf: TestContainerConf)
    extends ScalaTestWithActorTestKit(
      org.apache.pekko.actor
        .ActorSystem(
          "ReplicationJavaDSLIntegrationSpecA",
          ReplicationJavaDSLIntegrationSpec
            .config(ReplicationJavaDSLIntegrationSpec.DCA)
            .withFallback(testContainerConf.config))
        .toTyped)
    with AnyWordSpecLike
    with TestDbLifecycle
    with BeforeAndAfterAll
    with LogCapturing {
  import ReplicationJavaDSLIntegrationSpec._
  implicit val ec: ExecutionContext = system.executionContext

  def this() = this(new TestContainerConf)

  private val logger = LoggerFactory.getLogger(classOf[ReplicationIntegrationSpec])
  override def typedSystem: ActorSystem[_] = testKit.system

  private val systems = Seq[ActorSystem[_]](
    typedSystem,
    org.apache.pekko.actor
      .ActorSystem(
        "ReplicationJavaDSLIntegrationSpecB",
        ReplicationJavaDSLIntegrationSpec.config(DCB).withFallback(testContainerConf.config))
      .toTyped,
    org.apache.pekko.actor
      .ActorSystem(
        "ReplicationJavaDSLIntegrationSpecC",
        ReplicationJavaDSLIntegrationSpec.config(DCC).withFallback(testContainerConf.config))
      .toTyped)

  private val grpcPorts = SocketUtil.temporaryServerAddresses(systems.size, "127.0.0.1").map(_.getPort)
  private val allDcsAndPorts = Seq(DCA, DCB, DCC).zip(grpcPorts)
  private val allReplicas = allDcsAndPorts.map {
    case (id, port) =>
      Replica.create(id, 2, GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false))
  }

  private val testKitsPerDc = Map(DCA -> testKit, DCB -> ActorTestKit(systems(1)), DCC -> ActorTestKit(systems(2)))
  private val systemPerDc = Map(DCA -> system, DCB -> systems(1), DCC -> systems(2))
  private var replicatedEventSourcingOverGrpcPerDc: Map[ReplicaId, Replication[LWWHelloWorld.Command]] = Map.empty
  private val entityIds = Set("one", "two", "three")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    // We can share the journal to save a bit of work, because the persistence id contains
    // the dc so is unique (this is ofc completely synthetic, the whole point of replication
    // over grpc is to replicate between different dcs/regions with completely separate databases).
    // The offset tables need to be separate though to not get conflicts on projection names
    systemPerDc.values.foreach { system =>
      val r2dbcProjectionSettings = R2dbcProjectionSettings(system)
      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${r2dbcProjectionSettings.timestampOffsetTableWithSchema}")),
        10.seconds)

    }
  }

  def startReplica(replicaSystem: ActorSystem[_], selfReplicaId: ReplicaId): Replication[LWWHelloWorld.Command] = {
    val settings = ReplicationSettings.create(
      classOf[LWWHelloWorld.Command],
      "hello-world-java",
      selfReplicaId,
      EventProducerSettings.apply(replicaSystem),
      allReplicas.toSet.asJava: java.util.Set[Replica],
      Duration.ofSeconds(10),
      8,
      R2dbcReplication.create(system))
    Replication.grpcReplication(settings, LWWHelloWorld.create _, replicaSystem)
  }

  "Replication over gRPC" should {
    "form three one node clusters" in {
      testKitsPerDc.values.foreach { testKit =>
        val cluster = Cluster(testKit.system)
        cluster.manager ! Join(cluster.selfMember.address)
        testKit.createTestProbe().awaitAssert {
          cluster.selfMember.status should ===(MemberStatus.Up)
        }
      }
    }

    "start three replicas" in {
      val replicasStarted = Future.sequence(allReplicas.zipWithIndex.map {
        case (replica, index) =>
          val system = systems(index)
          logger
            .infoN(
              "Starting replica [{}], system [{}] on port [{}]",
              replica.replicaId,
              system.name,
              replica.grpcClientSettings.defaultPort)
          val started = startReplica(system, replica.replicaId)
          val grpcPort = grpcPorts(index)

          // start producer server
          Http(system)
            .newServerAt("127.0.0.1", grpcPort)
            .bind(started.createSingleServiceHandler())
            .toScala
            .map { binding: ServerBinding =>
              binding.addToCoordinatedShutdown(Duration.ofSeconds(3), system)
              replica.replicaId -> started
            }

      })

      replicatedEventSourcingOverGrpcPerDc = replicasStarted.futureValue.toMap
      logger.info("All three replication/producer services bound")
    }

    "replicate writes from one dc to the other two" in {
      val entityTypeKey = replicatedEventSourcingOverGrpcPerDc.values.head.entityTypeKey
      systemPerDc.keys.foreach { dc =>
        withClue(s"from ${dc.id}") {
          Future
            .sequence(entityIds.map { entityId =>
              logger.infoN("Updating greeting for [{}] from dc [{}]", entityId, dc.id)
              ClusterSharding
                .get(systemPerDc(dc))
                .entityRefFor(entityTypeKey, entityId)
                .ask(LWWHelloWorld.SetGreeting(s"hello 1 from ${dc.id}", _), Duration.ofSeconds(3))
                .toScala
            })
            .futureValue

          testKitsPerDc.values.foreach { testKit =>
            withClue(s"on ${system.name}") {
              val probe = testKit.createTestProbe()

              entityIds.foreach { entityId =>
                withClue(s"for entity id $entityId") {
                  val entityRef = ClusterSharding
                    .get(testKit.system)
                    .entityRefFor(entityTypeKey, entityId)

                  probe.awaitAssert({
                      entityRef
                        .ask(LWWHelloWorld.Get.apply, Duration.ofSeconds(10))
                        .toScala
                        .futureValue should ===(s"hello 1 from ${dc.id}")
                    }, 10.seconds)
                }
              }
            }
          }
        }
      }
    }
  }

  protected override def afterAll(): Unit = {
    logger.info("Shutting down all three DCs")
    systems.foreach(_.terminate()) // speed up termination by terminating all at the once
    // and then make sure they are completely shutdown
    systems.foreach { system =>
      ActorTestKit.shutdown(system)
    }
    super.afterAll()
  }
}
