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

import java.net.ServerSocket

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.grpc.GrpcClientSettings
import pekko.persistence.query.NoOffset
import pekko.projection.grpc.consumer.GrpcQuerySettings
import pekko.projection.grpc.internal.ConnectionException
import pekko.stream.scaladsl.Sink
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcReadJournalConnectionSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  private def unusedPort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  "GrpcReadJournal" should {
    "fail the eventsBySlices stream with a short connection failure when the producer is unavailable" in {
      val port = unusedPort()
      val streamId = "connection-failure-stream"
      val grpcReadJournal = GrpcReadJournal(
        GrpcQuerySettings(streamId),
        GrpcClientSettings.connectToServiceAt("127.0.0.1", port).withTls(false),
        protobufDescriptors = Nil)

      val failure = grpcReadJournal
        .eventsBySlices[String](streamId, 0, 1023, NoOffset)
        .runWith(Sink.ignore)
        .failed
        .futureValue

      failure shouldBe a[ConnectionException]
      failure shouldBe a[NoStackTrace]
      failure.getMessage should include(s"127.0.0.1:$port")
      failure.getMessage should include(streamId)
    }
  }
}
