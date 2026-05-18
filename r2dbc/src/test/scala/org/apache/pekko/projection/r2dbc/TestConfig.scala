/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object TestConfig {
  lazy val unresolvedConfig: Config = {
    val defaultConfig = ConfigFactory.load()
    val dialect = defaultConfig.getString("pekko.projection.r2dbc.dialect")

    val dialectConfig = dialect match {
      case "postgres" =>
        ConfigFactory.parseString("""
          pekko.persistence.r2dbc.connection-factory {
            driver = "postgres"
            host = "localhost"
            port = 5432
            user = "postgres"
            password = "postgres"
            database = "postgres"
          }
          """)
      case "yugabyte" =>
        ConfigFactory.parseString("""
          pekko.persistence.r2dbc.connection-factory {
            driver = "postgres"
            host = "localhost"
            port = 5433
            user = "yugabyte"
            password = "yugabyte"
            database = "yugabyte"
          }
          """)
      case "mysql" =>
        ConfigFactory.parseString("""
          pekko.persistence.r2dbc{
            connection-factory {
              driver = "mysql"
              host = "localhost"
              port = 3306
              user = "root"
              password = "root"
              database = "mysql"
            }
            db-timestamp-monotonic-increasing = on
            use-app-timestamp = on
          }
          """)
    }

    // using load here so that connection-factory can be overridden
    dialectConfig.withFallback(ConfigFactory.parseString("""
    pekko.persistence.journal.plugin = "pekko.persistence.r2dbc.journal"
    pekko.persistence.state.plugin = "pekko.persistence.r2dbc.state"
    pekko.persistence.r2dbc {
      query {
        refresh-interval = 1s
      }
    }
    pekko.actor.testkit.typed.default-timeout = 10s
    """))
  }

  lazy val config: Config = ConfigFactory.load(unresolvedConfig)
}
