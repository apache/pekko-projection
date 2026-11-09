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

package org.apache.pekko.projection.grpc.consumer

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.projection.grpc.consumer.javadsl.{ GrpcReadJournal => JavaGrpcReadJournal }
import pekko.projection.grpc.consumer.scaladsl.GrpcReadJournal
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

object GrpcReadJournalFactorySpec {
  val config = ConfigFactory
    .parseString("""
    pekko.projection.grpc.consumer {
      stream-id = "stream-from-config"
      client {
        host = "127.0.0.1"
        port = 8101
        use-tls = false
      }
    }
    """)
    .withFallback(ConfigFactory.load())
}

class GrpcReadJournalFactorySpec
    extends ScalaTestWithActorTestKit(GrpcReadJournalFactorySpec.config)
    with AnyWordSpecLike {

  "The scaladsl GrpcReadJournal factory" should {
    "use query settings from config when only descriptors are given" in {
      val journal = GrpcReadJournal(Nil)
      try journal.streamId shouldBe "stream-from-config"
      finally journal.close().futureValue
    }

    "use the given query settings with client settings from config" in {
      val journal = GrpcReadJournal(GrpcQuerySettings("explicit-stream"), Nil)
      try journal.streamId shouldBe "explicit-stream"
      finally journal.close().futureValue
    }
  }

  "The javadsl GrpcReadJournal factory" should {
    "use the given query settings with client settings from config" in {
      val journal =
        JavaGrpcReadJournal.create(system, GrpcQuerySettings.create("explicit-stream"), java.util.List.of())
      try journal.streamId() shouldBe "explicit-stream"
      finally journal.close().toCompletableFuture.get()
    }
  }
}
