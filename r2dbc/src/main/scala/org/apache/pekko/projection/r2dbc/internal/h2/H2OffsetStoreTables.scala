/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.internal.h2

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.DispatcherSelector
import pekko.annotation.InternalApi
import pekko.projection.r2dbc.R2dbcProjectionSettings
import com.typesafe.config.Config
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import io.r2dbc.spi.Result
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * INTERNAL API
 *
 * The H2 database runs in the same process, so its tables can't be created up front with a schema script. Like the
 * pekko-persistence-r2dbc plugins with the h2 dialect, the offset store creates the tables of its own settings before
 * the first connection is handed out.
 */
@InternalApi
private[projection] object H2OffsetStoreTables {

  /**
   * Wraps the connection factory so that the offset store tables of `settings` are created before the first
   * connection is handed out. Not when the connection is configured with a `url`, same as the persistence plugins.
   */
  def creatingTables(
      connectionFactory: ConnectionFactory,
      connectionFactoryConfig: Config,
      settings: R2dbcProjectionSettings): ConnectionFactory =
    if (connectionFactoryConfig.getString("h2.url").nonEmpty) connectionFactory
    else new TableCreatingConnectionFactory(connectionFactory, ddl(settings))

  /**
   * The H2 R2DBC driver blocks in surprising places, so the database calls run on the `h2.use-dispatcher` of the
   * connection factory, same as the persistence plugins.
   */
  def executionContext(system: ActorSystem[?], connectionFactoryConfig: Config): ExecutionContext =
    system.dispatchers.lookup(DispatcherSelector.fromConfig(connectionFactoryConfig.getString("h2.use-dispatcher")))

  def ddl(settings: R2dbcProjectionSettings): Seq[String] = {
    val schema = settings.schema.map(s => s"CREATE SCHEMA IF NOT EXISTS $s").toSeq

    val offsetTable =
      if (settings.isOffsetTableDefined)
        Seq(s"""CREATE TABLE IF NOT EXISTS ${settings.offsetTableWithSchema} (
          projection_name VARCHAR(255) NOT NULL,
          projection_key VARCHAR(255) NOT NULL,
          current_offset VARCHAR(255) NOT NULL,
          manifest VARCHAR(32) NOT NULL,
          mergeable BOOLEAN NOT NULL,
          last_updated BIGINT NOT NULL,
          PRIMARY KEY(projection_name, projection_key)
        )""")
      else Seq.empty

    val timestampOffsetTable =
      s"""CREATE TABLE IF NOT EXISTS ${settings.timestampOffsetTableWithSchema} (
          projection_name VARCHAR(255) NOT NULL,
          projection_key VARCHAR(255) NOT NULL,
          slice INT NOT NULL,
          persistence_id VARCHAR(255) NOT NULL,
          seq_nr BIGINT NOT NULL,
          timestamp_offset timestamp with time zone NOT NULL,
          timestamp_consumed timestamp with time zone NOT NULL,
          PRIMARY KEY(slice, projection_name, timestamp_offset, persistence_id, seq_nr)
        )"""

    val managementTable =
      s"""CREATE TABLE IF NOT EXISTS ${settings.managementTableWithSchema} (
          projection_name VARCHAR(255) NOT NULL,
          projection_key VARCHAR(255) NOT NULL,
          paused BOOLEAN NOT NULL,
          last_updated BIGINT NOT NULL,
          PRIMARY KEY(projection_name, projection_key)
        )"""

    schema ++ offsetTable ++ Seq(timestampOffsetTable, managementTable)
  }

  /**
   * Runs `ddl` once, on a connection of its own, before handing out the first connection. A failure is not cached,
   * so the next `create()` runs `ddl` again, for example after a transient lock on a file database.
   */
  private final class TableCreatingConnectionFactory(underlying: ConnectionFactory, ddl: Seq[String])
      extends ConnectionFactory {

    private val tablesCreated: Mono[Void] =
      Mono
        .usingWhen[Void, Connection](
          Mono.from[Connection](underlying.create()),
          (connection: Connection) =>
            Flux
              .fromIterable(ddl.asJava)
              .concatMap((statement: String) =>
                Flux
                  .from[Result](connection.createStatement(statement).execute())
                  .concatMap((result: Result) => result.getRowsUpdated))
              .`then`(),
          (connection: Connection) => connection.close())
        .cache(
          (_: Void) => Duration.ofMillis(Long.MaxValue),
          (_: Throwable) => Duration.ZERO,
          () => Duration.ofMillis(Long.MaxValue))

    override def create(): Publisher[? <: Connection] =
      tablesCreated.`then`(Mono.from[Connection](underlying.create()))

    override def getMetadata: ConnectionFactoryMetadata = underlying.getMetadata
  }
}
