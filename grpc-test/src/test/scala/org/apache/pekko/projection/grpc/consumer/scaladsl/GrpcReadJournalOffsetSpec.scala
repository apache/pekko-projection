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

import java.time.Instant

import org.apache.pekko
import pekko.persistence.query.TimestampOffset
import pekko.projection.grpc.internal.proto
import pekko.projection.grpc.internal.proto.PersistenceIdSeqNr
import com.google.protobuf.timestamp.Timestamp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcReadJournalOffsetSpec extends AnyWordSpecLike with Matchers {

  private val timestamp = Instant.parse("2023-09-11T10:15:30.123456Z")

  "GrpcReadJournal.timestampOffset" should {

    "populate seen from the enclosing event when the proto offset has no seen entries" in {
      val protoOffset = proto.Offset(Some(Timestamp(timestamp)), seen = Nil)

      GrpcReadJournal.timestampOffset(protoOffset, "TestEntity|a", 17L) shouldBe
      TimestampOffset(timestamp, Map("TestEntity|a" -> 17L))
    }

    "keep the seen entries of the proto offset when present" in {
      val protoOffset = proto.Offset(
        Some(Timestamp(timestamp)),
        seen = Seq(PersistenceIdSeqNr("TestEntity|a", 17L), PersistenceIdSeqNr("TestEntity|b", 3L)))

      GrpcReadJournal.timestampOffset(protoOffset, "TestEntity|a", 17L) shouldBe
      TimestampOffset(timestamp, Map("TestEntity|a" -> 17L, "TestEntity|b" -> 3L))
    }
  }
}
