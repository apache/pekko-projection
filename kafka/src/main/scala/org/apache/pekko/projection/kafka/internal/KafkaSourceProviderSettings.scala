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

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[projection] final case class KafkaSourceProviderSettings(readOffsetDelay: FiniteDuration)

/**
 * INTERNAL API
 */
@InternalApi
private[projection] object KafkaSourceProviderSettings {
  def apply(system: ActorSystem[_]): KafkaSourceProviderSettings = {
    fromConfig(system.classicSystem.settings.config.getConfig("pekko.projection.kafka"))
  }

  def fromConfig(config: Config): KafkaSourceProviderSettings = {
    val readOffsetDelay = config.getDuration("read-offset-delay", MILLISECONDS).millis
    KafkaSourceProviderSettings(readOffsetDelay)
  }
}
