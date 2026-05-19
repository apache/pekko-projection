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

package org.apache.pekko.projection.r2dbc.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement

@ApiMayChange
final class R2dbcSession(connection: Connection)(implicit ec: ExecutionContext, system: ActorSystem[_]) {

  def createStatement(sql: String): Statement =
    connection.createStatement(sql)

  def updateOne(statement: Statement): CompletionStage[java.lang.Long] =
    R2dbcExecutor.updateOneInTx(statement).map(java.lang.Long.valueOf)(ExecutionContext.parasitic).asJava

  def update(statements: java.util.List[Statement]): CompletionStage[java.util.List[java.lang.Long]] =
    R2dbcExecutor.updateInTx(statements.asScala.toVector).map(results =>
      results.map(java.lang.Long.valueOf).asJava).asJava

  def selectOne[A](statement: Statement)(mapRow: Row => A): CompletionStage[Optional[A]] =
    R2dbcExecutor.selectOneInTx(statement, mapRow).map(_.toJava)(ExecutionContext.parasitic).asJava

  def select[A](statement: Statement)(mapRow: Row => A): CompletionStage[java.util.List[A]] =
    R2dbcExecutor.selectInTx(statement, mapRow).map(_.asJava).asJava

}
