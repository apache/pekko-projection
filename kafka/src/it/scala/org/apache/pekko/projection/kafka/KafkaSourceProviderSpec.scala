/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.kafka

import java.lang.{ Long => JLong }

import scala.concurrent.Await
import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.typed.scaladsl.adapter._
import pekko.projection.MergeableOffset
import pekko.projection.kafka.scaladsl.KafkaSourceProvider
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import pekko.stream.testkit.scaladsl.TestSink

class KafkaSourceProviderSpec extends KafkaSpecBase {
  "KafkaSourceProviderSpec" must {
    "resume from provided offsets" in assertAllStagesStopped {
      val topic = createTopic()
      val groupId = createGroupId()
      val settings = consumerDefaults.withGroupId(groupId)

      Await.result(produce(topic, 1 to 100), remainingOrDefault)

      val provider = KafkaSourceProvider(system.toTyped, settings, Set(topic))
      val readOffsetsHandler =
        () =>
          Future.successful(Option(MergeableOffset(Map(KafkaOffsets.partitionToKey(topic, 0) -> JLong.valueOf(5L)))))
      val probe = Source
        .futureSource(provider.source(readOffsetsHandler))
        .runWith(TestSink.probe)

      probe.request(1)
      val first = probe.expectNext()
      first.offset() shouldBe 6L // next offset

      probe.cancel()
    }

    "resume from beginning offsets when none are provided" in assertAllStagesStopped {
      val topic = createTopic()
      val groupId = createGroupId()
      val settings = consumerDefaults.withGroupId(groupId)

      Await.result(produce(topic, 1 to 100), remainingOrDefault)

      val provider = KafkaSourceProvider(system.toTyped, settings, Set(topic))
      val readOffsetsHandler = () => Future.successful(None)
      val probe = Source
        .futureSource(provider.source(readOffsetsHandler))
        .runWith(TestSink.probe)

      probe.request(1)
      val first = probe.expectNext()
      first.offset() shouldBe 0L

      probe.cancel()
    }
  }
}
