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
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.utility.DockerImageName

/**
 * Use testcontainers to lazily provide a single CqlSession for all Cassandra tests
 */
final class ContainerSessionProvider extends CqlSessionProvider {
  import ContainerSessionProvider._

  override def connect()(implicit ec: ExecutionContext): Future[CqlSession] = started.map { _ =>
    CqlSession.builder
      .addContactEndPoint(new DefaultEndPoint(InetSocketAddress
        .createUnresolved(container.getContainerIpAddress, container.getFirstMappedPort.intValue())))
      .withLocalDatacenter("datacenter1")
      .build()
  }
}

object ContainerSessionProvider {
  private val disabled = java.lang.Boolean.getBoolean("disable-cassandra-testcontainer")

  private lazy val container: CassandraContainer[_] = new CassandraContainer(DockerImageName.parse("cassandra:3.11.9"))

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
