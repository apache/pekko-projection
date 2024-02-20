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

package org.apache.pekko.projection.kafka.internal

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.kafka.ConsumerSettings
import pekko.kafka.scaladsl.Consumer
import pekko.projection.MergeableOffset
import pekko.projection.ProjectionId
import pekko.projection.scaladsl.Handler
import pekko.projection.testkit.scaladsl.ProjectionTestKit
import pekko.projection.testkit.scaladsl.TestProjection
import pekko.stream.scaladsl.Source
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.wordspec.AnyWordSpecLike

object KafkaSourceProviderImplSpec {
  private val TestProjectionId = ProjectionId("test-projection", "00")

  def handler(probe: TestProbe[ConsumerRecord[String, String]],
      assertFunction: TestProbe[ConsumerRecord[String, String]] => Future[Done])
      : Handler[ConsumerRecord[String, String]] =
    new Handler[ConsumerRecord[String, String]] {
      override def process(env: ConsumerRecord[String, String]): Future[Done] = {
        probe.ref ! env
        assertFunction(probe)
      }
    }

  private class TestMetadataClientAdapter(partitions: Int) extends MetadataClientAdapter {
    override def getBeginningOffsets(assignedTps: Set[TopicPartition]): Future[Map[TopicPartition, Long]] =
      Future.successful((0 until partitions).map(i => new TopicPartition("topic", i) -> 0L).toMap)
    override def numPartitions(topics: Set[String]): Future[Int] = Future.successful(partitions)
    override def stop(): Unit = ()
  }
}

class KafkaSourceProviderImplSpec extends ScalaTestWithActorTestKit with LogCapturing with AnyWordSpecLike {
  import KafkaSourceProviderImplSpec._

  val projectionTestKit: ProjectionTestKit = ProjectionTestKit(system)
  implicit val ec: ExecutionContext = system.classicSystem.dispatcher

  "The KafkaSourceProviderImpl" must {

    "successfully verify offsets from assigned partitions" in {
      val topic = "topic"
      val partitions = 2
      val settings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers("localhost:9092")
        .withGroupId("group-id")
      val metadataClient = new TestMetadataClientAdapter(partitions)
      val tp0 = new TopicPartition(topic, 0)
      val tp1 = new TopicPartition(topic, 1)

      val consumerRecords =
        for (n <- 0 to 10; tp <- List(tp0, tp1))
          yield new ConsumerRecord(tp.topic(), tp.partition(), n, n.toString, n.toString)

      val consumerSource = Source(consumerRecords)
        .mapMaterializedValue(_ => Consumer.NoopControl)

      val provider =
        new KafkaSourceProviderImpl(
          system,
          settings,
          Set(topic),
          () => metadataClient,
          KafkaSourceProviderSettings(system)) {
          override protected[internal] def _source(
              readOffsets: () => Future[Option[MergeableOffset[java.lang.Long]]],
              numPartitions: Int,
              metadataClient: MetadataClientAdapter): Source[ConsumerRecord[String, String], Consumer.Control] =
            consumerSource
        }

      val probe = testKit.createTestProbe[ConsumerRecord[String, String]]()
      val records = Set.empty[ConsumerRecord[String, String]]
      val projection = TestProjection(TestProjectionId, provider,
        () =>
          handler(probe,
            p => {
              records ++= p.receiveMessage()
              Future.successful(Done)
            }))

      projectionTestKit.runWithTestSink(projection) { sinkProbe =>
        provider.partitionHandler.onAssign(Set(tp0, tp1), null)
        provider.partitionHandler.onRevoke(Set.empty, null)

        sinkProbe.request(10)
        sinkProbe.expectNextN(10)

        withClue("checking: processed records contain 5 from each partition") {
          records.toSeq.length shouldBe 10
          records.count(_.partition() == tp0.partition()) shouldBe 5
          records.count(_.partition() == tp1.partition()) shouldBe 5
        }

        // assign only tp0 to this projection
        provider.partitionHandler.onAssign(Set(tp0), null)
        provider.partitionHandler.onRevoke(Set(tp1), null)

        // drain any remaining messages that were processed before rebalance because of async stages in the internal
        // projection stream
        eventually(probe.expectNoMessage(1.millis))

        // only records from partition 0 should remain, because the rest were filtered
        sinkProbe.request(5)
        sinkProbe.expectNextN(5)

        withClue("checking: after rebalance processed records should only have records from partition 0") {
          records.count(_.partition() == tp0.partition()) shouldBe 10
          records.count(_.partition() == tp1.partition()) shouldBe 5
        }
      }
    }
  }
}
