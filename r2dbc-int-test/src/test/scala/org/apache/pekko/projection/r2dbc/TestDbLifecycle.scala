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

package org.apache.pekko.projection.r2dbc

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.persistence.Persistence
import pekko.persistence.r2dbc.Dialect
import pekko.persistence.r2dbc.JournalSettings
import pekko.persistence.r2dbc.StateSettings
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.projection.r2dbc.internal.R2dbcProjectionImpl
import com.typesafe.config.ConfigFactory
import io.r2dbc.spi.R2dbcException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

object TestDbLifecycle {

  // H2 error codes of a table that doesn't exist: TABLE_OR_VIEW_NOT_FOUND_1, _WITH_CANDIDATES_2 and _DATABASE_EMPTY_1
  def isH2TableNotFound(e: R2dbcException): Boolean =
    Set(42102, 42103, 42104).contains(e.getErrorCode)

  /**
   * Deletes all rows of `table`. The H2 persistence plugins create their tables when they are first used, which can
   * be after a test harness deletes from them, so with H2 a table that doesn't exist yet counts as empty.
   */
  def deleteAll(r2dbcExecutor: R2dbcExecutor, dialect: Dialect, table: String): Unit = {
    val deleted = r2dbcExecutor.updateOne("beforeAll delete")(_.createStatement(s"delete from $table"))
    val result =
      if (dialect == Dialect.H2)
        deleted.recover {
          case e: R2dbcException if isH2TableNotFound(e) => 0L
        }(ExecutionContext.parasitic)
      else deleted
    Await.result(result, 10.seconds)
  }
}

trait TestDbLifecycle extends BeforeAndAfterAll { this: Suite =>

  def typedSystem: ActorSystem[?]

  def testConfigPath: String = "pekko.projection.r2dbc"

  lazy val r2dbcProjectionSettings: R2dbcProjectionSettings =
    R2dbcProjectionSettings(typedSystem.settings.config.getConfig(testConfigPath))

  // same connection factory and executor as the projections, which creates the offset store tables with H2
  lazy val r2dbcExecutor: R2dbcExecutor =
    R2dbcProjectionImpl.createR2dbcExecutor(
      R2dbcProjectionImpl.connectionFactory(r2dbcProjectionSettings, ConfigFactory.empty())(typedSystem),
      r2dbcProjectionSettings,
      ConfigFactory.empty())(typedSystem)

  lazy val persistenceExt: Persistence = Persistence(typedSystem)

  override protected def beforeAll(): Unit = {
    val journalSettings: JournalSettings =
      new JournalSettings(typedSystem.settings.config.getConfig("pekko.persistence.r2dbc.journal"))
    val stateSettings: StateSettings =
      new StateSettings(typedSystem.settings.config.getConfig("pekko.persistence.r2dbc.state"))
    val dialect = r2dbcProjectionSettings.dialect
    TestDbLifecycle.deleteAll(r2dbcExecutor, dialect, journalSettings.journalTableWithSchema)
    TestDbLifecycle.deleteAll(r2dbcExecutor, dialect, stateSettings.durableStateTableWithSchema)
    if (r2dbcProjectionSettings.isOffsetTableDefined)
      TestDbLifecycle.deleteAll(r2dbcExecutor, dialect, r2dbcProjectionSettings.offsetTableWithSchema)
    TestDbLifecycle.deleteAll(r2dbcExecutor, dialect, r2dbcProjectionSettings.timestampOffsetTableWithSchema)
    TestDbLifecycle.deleteAll(r2dbcExecutor, dialect, r2dbcProjectionSettings.managementTableWithSchema)
    super.beforeAll()
  }

}
