/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.projection.grpc.consumer.scaladsl

import java.io.IOException
import java.net.ServerSocket

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.grpc.GrpcClientSettings
import pekko.grpc.GrpcServiceException
import pekko.http.scaladsl.Http
import pekko.persistence.query.NoOffset
import pekko.projection.grpc.consumer.GrpcQuerySettings
import pekko.projection.grpc.internal.ConnectionException
import pekko.projection.grpc.internal.proto
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import io.grpc.Status
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcReadJournalConnectionSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseString("pekko.http.server.enable-http2 = on"))
    with AnyWordSpecLike
    with LogCapturing {

  private def unusedPort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def grpcReadJournal(streamId: String, port: Int): GrpcReadJournal =
    GrpcReadJournal(
      GrpcQuerySettings(streamId),
      GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false),
      protobufDescriptors = Nil)

  private def eventsBySlicesFailure(streamId: String, port: Int): Throwable =
    grpcReadJournal(streamId, port)
      .eventsBySlices[String](streamId, 0, 1023, NoOffset)
      .runWith(Sink.ignore)
      .failed
      .futureValue

  // a producer that is reachable, but fails the stream with the given status
  private class FailingEventProducerService(status: Status) extends proto.EventProducerService {
    override def eventsBySlices(in: Source[proto.StreamIn, NotUsed]): Source[proto.StreamOut, NotUsed] =
      Source.failed(new GrpcServiceException(status))

    override def eventTimestamp(in: proto.EventTimestampRequest): Future[proto.EventTimestampResponse] =
      Future.failed(new GrpcServiceException(status))

    override def loadEvent(in: proto.LoadEventRequest): Future[proto.LoadEventResponse] =
      Future.failed(new GrpcServiceException(status))
  }

  "GrpcReadJournal" should {
    "fail the eventsBySlices stream with a short connection failure when the producer is unavailable" in {
      val port = unusedPort()
      val streamId = "connection-failure-stream"

      val failure = eventsBySlicesFailure(streamId, port)

      failure shouldBe a[ConnectionException]
      failure shouldBe a[NoStackTrace]
      failure.getMessage should include(s"127.0.0.1:$port")
      failure.getMessage should include(streamId)

      // the original status and its transport cause are retained
      val status = failure.asInstanceOf[ConnectionException].status
      status.getCode shouldBe Status.Code.UNAVAILABLE
      status.getCause shouldBe a[IOException]
      failure.getMessage should include("UNAVAILABLE")
      failure.getMessage should include(status.getCause.toString)
    }

    "not report UNAVAILABLE returned by the producer as a connection failure" in {
      val streamId = "producer-unavailable-stream"
      val description = "producer is not ready"
      val handler =
        proto.EventProducerServiceHandler(
          new FailingEventProducerService(Status.UNAVAILABLE.withDescription(description)))
      // bind to port 0 and use the port that was actually bound, so no other test can claim it in between
      val binding = Http().newServerAt("127.0.0.1", 0).bind(handler).futureValue
      val port = binding.localAddress.getPort

      try {
        val failure = eventsBySlicesFailure(streamId, port)

        failure should not be a[ConnectionException]
        failure.getMessage should include(streamId)
        failure.getCause shouldBe a[GrpcServiceException]
        val status = failure.getCause.asInstanceOf[GrpcServiceException].status
        status.getCode shouldBe Status.Code.UNAVAILABLE
        status.getDescription shouldBe description
      } finally {
        binding.terminate(3.seconds).futureValue
      }
    }
  }

  "ConnectionException.isTransportFailure" should {
    "only accept UNAVAILABLE caused by an I/O failure" in {
      val ioFailure = new IOException("Connection refused")
      ConnectionException.isTransportFailure(Status.UNAVAILABLE.withCause(ioFailure)) shouldBe true
      ConnectionException.isTransportFailure(
        Status.UNAVAILABLE.withCause(new RuntimeException(ioFailure))) shouldBe true
      ConnectionException.isTransportFailure(Status.UNAVAILABLE.withDescription("try later")) shouldBe false
      ConnectionException.isTransportFailure(
        Status.UNAVAILABLE.withCause(new IllegalStateException("boom"))) shouldBe false
      ConnectionException.isTransportFailure(Status.INTERNAL.withCause(ioFailure)) shouldBe false
    }
  }
}
