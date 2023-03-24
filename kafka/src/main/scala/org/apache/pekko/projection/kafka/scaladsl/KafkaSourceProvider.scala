/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.kafka.scaladsl

import java.lang.{ Long => JLong }

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.kafka.ConsumerSettings
import pekko.projection.MergeableOffset
import pekko.projection.kafka.internal.KafkaSourceProviderImpl
import pekko.projection.kafka.internal.KafkaSourceProviderSettings
import pekko.projection.kafka.internal.MetadataClientAdapterImpl
import pekko.projection.scaladsl.SourceProvider
import org.apache.kafka.clients.consumer.ConsumerRecord

@ApiMayChange
object KafkaSourceProvider {

  /**
   * Create a [[SourceProvider]] that resumes from externally managed offsets
   */
  def apply[K, V](
      system: ActorSystem[_],
      settings: ConsumerSettings[K, V],
      topics: Set[String]): SourceProvider[MergeableOffset[JLong], ConsumerRecord[K, V]] =
    new KafkaSourceProviderImpl[K, V](
      system,
      settings,
      topics,
      () => new MetadataClientAdapterImpl(system, settings),
      KafkaSourceProviderSettings(system))
}
