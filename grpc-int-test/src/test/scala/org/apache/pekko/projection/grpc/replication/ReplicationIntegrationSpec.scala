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
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.grpc.GrpcClientSettings
import pekko.http.scaladsl.Http
import pekko.persistence.typed.ReplicaId
import pekko.persistence.typed.crdt.LwwTime
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.projection.grpc.TestContainerConf
import pekko.projection.grpc.TestDbLifecycle
import pekko.projection.grpc.producer.EventProducerSettings
import pekko.projection.grpc.replication
import pekko.projection.grpc.replication.scaladsl.Replica
import pekko.projection.grpc.replication.scaladsl.ReplicatedBehaviors
import pekko.projection.grpc.replication.scaladsl.Replication
import pekko.projection.grpc.replication.scaladsl.ReplicationSettings
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.scaladsl.R2dbcReplication
import pekko.testkit.SocketUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object ReplicationIntegrationSpec {

  private def config(dc: ReplicaId): Config =
    ConfigFactory.parseString(s"""
       pekko.actor.provider = cluster
       pekko.actor {
         serialization-bindings {
           "${classOf[replication.ReplicationIntegrationSpec].getName}$$LWWHelloWorld$$Event" = jackson-json
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

    def apply(replicatedBehaviors: ReplicatedBehaviors[Command, Event, State]) =
      replicatedBehaviors.setup { replicationContext =>
        EventSourcedBehavior[Command, Event, State](
          replicationContext.persistenceId,
          State.initial,
          {
            case (State(greeting, _), Get(replyTo)) =>
              replyTo ! greeting
              Effect.none
            case (state, SetGreeting(greeting, replyTo)) =>
              Effect
                .persist(
                  GreetingChanged(
                    greeting,
                    state.timestamp.increase(replicationContext.currentTimeMillis(), replicationContext.replicaId)))
                .thenRun((_: State) => replyTo ! Done)
          },
          {
            case (currentState, GreetingChanged(newGreeting, newTimestamp)) =>
              if (newTimestamp.isAfter(currentState.timestamp))
                State(newGreeting, newTimestamp)
              else currentState
          })
      }
  }
}

class ReplicationIntegrationSpec(testContainerConf: TestContainerConf)
    extends ScalaTestWithActorTestKit(
      org.apache.pekko.actor
        .ActorSystem(
          "ReplicationIntegrationSpecA",
          ReplicationIntegrationSpec
            .config(ReplicationIntegrationSpec.DCA)
            .withFallback(testContainerConf.config))
        .toTyped)
    with AnyWordSpecLike
    with TestDbLifecycle
    with BeforeAndAfterAll
    with LogCapturing {
  import ReplicationIntegrationSpec._
  implicit val ec: ExecutionContext = system.executionContext

  def this() = this(new TestContainerConf)

  private val logger = LoggerFactory.getLogger(classOf[ReplicationIntegrationSpec])
  override def typedSystem: ActorSystem[_] = testKit.system

  private val systems = Seq[ActorSystem[_]](
    typedSystem,
    org.apache.pekko.actor
      .ActorSystem(
        "ReplicationIntegrationSpecB",
        ReplicationIntegrationSpec.config(DCB).withFallback(testContainerConf.config))
      .toTyped,
    org.apache.pekko.actor
      .ActorSystem(
        "ReplicationIntegrationSpecC",
        ReplicationIntegrationSpec.config(DCC).withFallback(testContainerConf.config))
      .toTyped)

  private val grpcPorts = SocketUtil.temporaryServerAddresses(systems.size, "127.0.0.1").map(_.getPort)
  private val allDcsAndPorts = Seq(DCA, DCB, DCC).zip(grpcPorts)
  private val allReplicas = allDcsAndPorts.map {
    case (id, port) =>
      Replica(id, 2, GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false))
  }.toSet

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
    val settings = ReplicationSettings[LWWHelloWorld.Command](
      "hello-world",
      selfReplicaId,
      EventProducerSettings(replicaSystem),
      allReplicas,
      10.seconds,
      8,
      R2dbcReplication())
    Replication.grpcReplication(settings)(ReplicationIntegrationSpec.LWWHelloWorld.apply)(replicaSystem)
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
            .map(_.addToCoordinatedShutdown(3.seconds)(system))(system.executionContext)
            .map(_ => replica.replicaId -> started)
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
              ClusterSharding(systemPerDc(dc))
                .entityRefFor(entityTypeKey, entityId)
                .ask(LWWHelloWorld.SetGreeting(s"hello 1 from ${dc.id}", _))
            })
            .futureValue

          testKitsPerDc.values.foreach { testKit =>
            withClue(s"on ${system.name}") {
              val probe = testKit.createTestProbe()

              entityIds.foreach { entityId =>
                withClue(s"for entity id $entityId") {
                  val entityRef = ClusterSharding(testKit.system)
                    .entityRefFor(entityTypeKey, entityId)

                  probe.awaitAssert({
                      entityRef
                        .ask(LWWHelloWorld.Get.apply)
                        .futureValue should ===(s"hello 1 from ${dc.id}")
                    }, 10.seconds)
                }
              }
            }
          }
        }
      }
    }

    "replicate concurrent writes to the other DCs" in (2 to 4).foreach { greetingNo =>
      withClue(s"Greeting $greetingNo") {
        val entityTypeKey = replicatedEventSourcingOverGrpcPerDc.values.head.entityTypeKey
        Future
          .sequence(systemPerDc.keys.map { dc =>
            withClue(s"from ${dc.id}") {
              Future.sequence(entityIds.map { entityId =>
                logger.infoN("Updating greeting for [{}] from dc [{}]", entityId, dc.id)
                ClusterSharding(systemPerDc(dc))
                  .entityRefFor(entityTypeKey, entityId)
                  .ask(LWWHelloWorld.SetGreeting(s"hello $greetingNo from ${dc.id}", _))
              })
            }
          })
          .futureValue // all three updated in roughly parallel

        // All 3 should eventually arrive at the same value
        testKit
          .createTestProbe()
          .awaitAssert(
            {
              entityIds.foreach { entityId =>
                withClue(s"for entity id $entityId") {
                  testKitsPerDc.values.map { testKit =>
                    val entityRef = ClusterSharding(testKit.system)
                      .entityRefFor(entityTypeKey, entityId)

                    entityRef
                      .ask(LWWHelloWorld.Get.apply)
                      .futureValue
                  }.toSet should have size 1
                }
              }
            },
            20.seconds)
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
