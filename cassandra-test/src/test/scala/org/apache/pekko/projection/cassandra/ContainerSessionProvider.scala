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

package org.apache.pekko.projection.cassandra

import java.net.InetSocketAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import org.apache.pekko.stream.connectors.cassandra.CqlSessionProvider
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.utility.DockerImageName

/**
 * Use testcontainers to lazily provide a single CqlSession for all Cassandra tests
 */
final class ContainerSessionProvider extends CqlSessionProvider {
  import ContainerSessionProvider._

  override def connect()(implicit ec: ExecutionContext): Future[CqlSession] = started.map { _ =>
    CqlSession.builder
      .addContactEndPoint(new DefaultEndPoint(InetSocketAddress
        .createUnresolved(container.getHost, container.getFirstMappedPort.intValue())))
      .withLocalDatacenter("datacenter1")
      .build()
  }
}

object ContainerSessionProvider {
  private val disabled = java.lang.Boolean.getBoolean("disable-cassandra-testcontainer")

  private lazy val container: CassandraContainer = new CassandraContainer(DockerImageName.parse("cassandra:5.0.1"))

  lazy val started: Future[Unit] = {
    if (disabled)
      Future.successful(())
    else
      Future.fromTry(Try(container.start()))
  }

  val Config =
    if (disabled)
      ""
    else
      """
      pekko.projection.cassandra.session-config.session-provider = "org.apache.pekko.projection.cassandra.ContainerSessionProvider"
      """
}
