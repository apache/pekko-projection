/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.producer

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.annotation.ApiMayChange
import com.typesafe.config.Config

@ApiMayChange
object EventProducerSettings {
  def apply(system: ActorSystem[_]): EventProducerSettings =
    apply(system.settings.config.getConfig("pekko.projection.grpc.producer"))

  def apply(config: Config): EventProducerSettings = {
    val queryPluginId: String = config.getString("query-plugin-id")
    val transformationParallelism: Int =
      config.getInt("transformation-parallelism")

    new EventProducerSettings(queryPluginId, transformationParallelism)
  }

  /** Java API */
  def create(system: ActorSystem[_]): EventProducerSettings = apply(system)

  /** Java API */
  def create(config: Config): EventProducerSettings = apply(config)
}

@ApiMayChange
final case class EventProducerSettings(queryPluginId: String, transformationParallelism: Int) {
  require(transformationParallelism >= 1, "Configuration property [transformation-parallelism] must be >= 1.")

}
