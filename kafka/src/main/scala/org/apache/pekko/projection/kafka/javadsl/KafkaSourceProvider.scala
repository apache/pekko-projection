/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.kafka.javadsl

import java.lang.{ Long => JLong }

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.kafka.ConsumerSettings
import pekko.projection.MergeableOffset
import pekko.projection.javadsl.SourceProvider
import pekko.projection.kafka.internal.KafkaSourceProviderImpl
import pekko.projection.kafka.internal.KafkaSourceProviderSettings
import pekko.projection.kafka.internal.MetadataClientAdapterImpl
import org.apache.kafka.clients.consumer.ConsumerRecord

@ApiMayChange
object KafkaSourceProvider {

  /**
   * Create a [[SourceProvider]] that resumes from externally managed offsets
   */
  def create[K, V](
      system: ActorSystem[_],
      settings: ConsumerSettings[K, V],
      topics: java.util.Set[String]): SourceProvider[MergeableOffset[JLong], ConsumerRecord[K, V]] = {
    import pekko.util.ccompat.JavaConverters._
    new KafkaSourceProviderImpl[K, V](
      system,
      settings,
      topics.asScala.toSet,
      () => new MetadataClientAdapterImpl(system, settings),
      KafkaSourceProviderSettings(system))
  }
}
