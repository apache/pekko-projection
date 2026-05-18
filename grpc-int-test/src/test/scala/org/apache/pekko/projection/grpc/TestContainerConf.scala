/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc

import org.apache.pekko
import pekko.testkit.SocketUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy

class TestContainerConf {
  val grpcPort: Int = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

  private val container: PostgreSQLContainer[_] = new PostgreSQLContainer("postgres:13.1")
  container.withInitScript("db/default-init.sql")
  container.withStartupCheckStrategy(new IsRunningStartupCheckStrategy)
  container.withStartupAttempts(5)
  container.start()

  def config: Config =
    ConfigFactory
      .parseString(s"""
     org.apache.pekko.http.server.enable-http2 = on
     org.apache.pekko.projection.grpc {
       consumer.client {
         host = "127.0.0.1"
         port = $grpcPort
         use-tls = false
       }
       producer {
         query-plugin-id = "pekko.persistence.r2dbc.query"
       }
     }
     org.apache.pekko.persistence.r2dbc {
       # yugabyte or postgres
       dialect = "postgres"
       connection-factory {
         driver = "postgres"
         host = "${container.getHost}"
         port = ${container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}
         database = "${container.getDatabaseName}"
         user = "${container.getUsername}"
         password = "${container.getPassword}"
       }
     }
     """)
      .withFallback(ConfigFactory.load("persistence.conf"))

  def stop(): Unit = container.stop()
}
