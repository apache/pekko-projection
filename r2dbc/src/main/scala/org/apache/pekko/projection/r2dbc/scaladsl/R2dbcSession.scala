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

package org.apache.pekko.projection.r2dbc.scaladsl

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.persistence.r2dbc.ConnectionFactoryProvider
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.R2dbcProjectionImpl
import com.typesafe.config.ConfigFactory
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import org.slf4j.LoggerFactory

@ApiMayChange
object R2dbcSession {
  private val log = LoggerFactory.getLogger(classOf[R2dbcSession])

  /**
   * Runs the passed function using a R2dbcSession with a new transaction. The connection is closed and the
   * transaction is committed at the end or rolled back in case of failures.
   *
   * The connection factory is the one configured by `pekko.projection.r2dbc.use-connection-factory`.
   *
   * @since 2.0.0
   */
  def withSession[A](system: ActorSystem[?])(fun: R2dbcSession => Future[A]): Future[A] =
    runWithSession(system, None, fun)

  /**
   * Runs the passed function using a R2dbcSession with a new transaction. The connection is closed and the
   * transaction is committed at the end or rolled back in case of failures.
   *
   * @param connectionFactoryConfigPath config path of the connection factory to use, for example
   *   `pekko.persistence.r2dbc.connection-factory`
   * @since 2.0.0
   */
  def withSession[A](system: ActorSystem[?], connectionFactoryConfigPath: String)(
      fun: R2dbcSession => Future[A]): Future[A] =
    runWithSession(system, Some(connectionFactoryConfigPath), fun)

  private def runWithSession[A](
      system: ActorSystem[?],
      connectionFactoryConfigPath: Option[String],
      fun: R2dbcSession => Future[A]): Future[A] =
    try {
      val defaultSettings = R2dbcProjectionSettings(system)
      val settings =
        connectionFactoryConfigPath.fold(defaultSettings)(path => defaultSettings.withUseConnectionFactory(path))
      val connectionFactory = ConnectionFactoryProvider(system).connectionFactoryFor(settings.useConnectionFactory)
      val r2dbcExecutor =
        R2dbcProjectionImpl.createR2dbcExecutor(connectionFactory, settings, ConfigFactory.empty(), log)(system)
      r2dbcExecutor.withConnection("R2dbcSession") { connection =>
        fun(new R2dbcSession(connection)(system.executionContext, system))
      }
    } catch {
      // config errors are reported as a failed Future, also for javadsl callers using the CompletionStage
      case NonFatal(e) => Future.failed(e)
    }
}

final class R2dbcSession(val connection: Connection)(implicit val ec: ExecutionContext, val system: ActorSystem[?]) {

  def createStatement(sql: String): Statement =
    connection.createStatement(sql)

  def updateOne(statement: Statement): Future[Long] =
    R2dbcExecutor.updateOneInTx(statement)

  def update(statements: immutable.IndexedSeq[Statement]): Future[immutable.IndexedSeq[Long]] =
    R2dbcExecutor.updateInTx(statements)

  def selectOne[A](statement: Statement)(mapRow: Row => A): Future[Option[A]] =
    R2dbcExecutor.selectOneInTx(statement, mapRow)

  def select[A](statement: Statement)(mapRow: Row => A): Future[immutable.IndexedSeq[A]] =
    R2dbcExecutor.selectInTx(statement, mapRow)

}
