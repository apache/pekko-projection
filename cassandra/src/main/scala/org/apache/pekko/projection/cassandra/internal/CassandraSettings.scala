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

package org.apache.pekko.projection.cassandra.internal

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[projection] case class CassandraSettings(config: Config) {
  val keyspace: String = config.getString("offset-store.keyspace")
  val table: String = config.getString("offset-store.table")
  val managementTable: String = config.getString("offset-store.management-table")
  val sessionConfigPath: String = config.getString("session-config-path")
  val profile: String = "pekko-projection-cassandra-profile"
}

/**
 * INTERNAL API
 */
@InternalApi
private[projection] object CassandraSettings {

  def apply(system: ActorSystem[_]): CassandraSettings =
    CassandraSettings(system.settings.config.getConfig("pekko.projection.cassandra"))
}
