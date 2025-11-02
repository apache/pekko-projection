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

package org.apache.pekko.projection.kafka

import java.lang.{ Long => JLong }

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.projection.MergeableOffset
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition

@ApiMayChange
object KafkaOffsets {

  private val separator = "-"
  private val RegexTp = """(.+)-(\d+)""".r

  def toMergeableOffset(record: ConsumerRecord[_, _]): MergeableOffset[JLong] = {
    val key = partitionToKey(new TopicPartition(record.topic(), record.partition()))
    new MergeableOffset[JLong](Map(key -> record.offset()))
  }

  def partitionToKey(topic: String, partition: Int): String = topic + separator + partition
  def partitionToKey(tp: TopicPartition): String = partitionToKey(tp.topic(), tp.partition())

  def keyToPartition(key: String): TopicPartition = key match {
    case RegexTp(topic, partition) => new TopicPartition(topic, partition.toInt)
    case _                         =>
      throw new IllegalArgumentException(s"Row entry name ($key) must match pattern: ${RegexTp.pattern.toString}")
  }

  def partitions(groupOffsets: MergeableOffset[JLong]): Set[TopicPartition] =
    groupOffsets.entries.keys.map(keyToPartition).toSet

}
