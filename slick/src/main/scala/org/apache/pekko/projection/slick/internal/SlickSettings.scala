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

package org.apache.pekko.projection.slick.internal

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[projection] case class SlickSettings(config: Config) {

  val schema: Option[String] =
    Option(config.getString("offset-store.schema")).filterNot(_.trim.isEmpty)

  val table: String = config.getString("offset-store.table")

  val managementTable: String = config.getString("offset-store.management-table")

  lazy val useLowerCase =
    config.getBoolean("offset-store.use-lowercase-schema")
}

/**
 * INTERNAL API
 */
@InternalApi
private[projection] object SlickSettings {

  val configPath = "pekko.projection.slick"

  def apply(system: ActorSystem[_]): SlickSettings =
    SlickSettings(system.settings.config.getConfig(configPath))

}
