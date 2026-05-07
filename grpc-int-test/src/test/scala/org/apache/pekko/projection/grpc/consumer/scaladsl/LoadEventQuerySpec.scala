/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.consumer.scaladsl

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorSystem
import pekko.grpc.GrpcClientSettings
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.projection.grpc.TestContainerConf
import pekko.projection.grpc.TestDbLifecycle
import pekko.projection.grpc.TestEntity
import pekko.projection.grpc.TestData
import pekko.projection.grpc.consumer.GrpcQuerySettings
import pekko.projection.grpc.producer.EventProducerSettings
import pekko.projection.grpc.producer.scaladsl.EventProducer
import pekko.projection.grpc.producer.scaladsl.EventProducer.EventProducerSource
import pekko.projection.grpc.producer.scaladsl.EventProducer.Transformation
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class LoadEventQuerySpec(testContainerConf: TestContainerConf)
    extends ScalaTestWithActorTestKit(testContainerConf.config)
    with AnyWordSpecLike
    with TestDbLifecycle
    with TestData
    with BeforeAndAfterAll
    with LogCapturing {

  def this() = this(new TestContainerConf)

  override def typedSystem: ActorSystem[_] = system
  private implicit val ec: ExecutionContext = system.executionContext
  private val entityType = nextEntityType()
  private val streamId = "stream_id_" + entityType

  protected override def afterAll(): Unit = {
    super.afterAll()
    testContainerConf.stop()
  }

  class TestFixture {

    val replyProbe = createTestProbe[Done]()
    val pid = nextPid(entityType)

    lazy val entity = spawn(TestEntity(pid))

    lazy val grpcReadJournal = GrpcReadJournal(
      GrpcQuerySettings(streamId),
      GrpcClientSettings
        .connectToServiceAt("127.0.0.1", testContainerConf.grpcPort)
        .withTls(false),
      protobufDescriptors = Nil)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val transformation =
      Transformation.empty.registerAsyncMapper((event: String) => {
        if (event.contains("*"))
          Future.successful(None)
        else
          Future.successful(Some(event.toUpperCase))
      })

    val eventProducerSource = EventProducerSource(entityType, streamId, transformation, EventProducerSettings(system))

    val eventProducerService =
      EventProducer.grpcServiceHandler(eventProducerSource)

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(eventProducerService)

    val bound =
      Http()
        .newServerAt("127.0.0.1", testContainerConf.grpcPort)
        .bind(service)
        .map(_.addToCoordinatedShutdown(3.seconds))

    bound.futureValue
  }

  "GrpcReadJournal with LoadEventQuery" must {
    "load event" in new TestFixture {
      entity ! TestEntity.Persist("a")
      entity ! TestEntity.Persist("b")
      entity ! TestEntity.Ping(replyProbe.ref)
      replyProbe.receiveMessage()

      grpcReadJournal
        .loadEnvelope[String](pid.id, sequenceNr = 1L)
        .futureValue
        .event shouldBe "A"

      grpcReadJournal
        .loadEnvelope[String](pid.id, sequenceNr = 2L)
        .futureValue
        .event shouldBe "B"
    }

    "load filtered event" in new TestFixture {
      entity ! TestEntity.Persist("a*")
      entity ! TestEntity.Ping(replyProbe.ref)
      replyProbe.receiveMessage()

      val env = grpcReadJournal
        .loadEnvelope[String](pid.id, sequenceNr = 1L)
        .futureValue
      env.eventOption.isEmpty shouldBe true
      env.eventMetadata shouldBe Some(NotUsed)
    }

    "handle missing event as NOT_FOUND" in new TestFixture {
      val status =
        intercept[StatusRuntimeException] {
          Await.result(grpcReadJournal.loadEnvelope[String](pid.id, sequenceNr = 13L), replyProbe.remainingOrDefault)
          fail("Expected NOT_FOUND")
        }.getStatus
      status.getCode shouldBe Status.NOT_FOUND.getCode
    }
  }

}
