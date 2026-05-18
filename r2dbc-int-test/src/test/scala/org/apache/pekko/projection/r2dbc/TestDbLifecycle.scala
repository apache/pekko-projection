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

import scala.concurrent.Await
import scala.concurrent.duration._
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.persistence.Persistence
import pekko.persistence.r2dbc.ConnectionFactoryProvider
import pekko.persistence.r2dbc.JournalSettings
import pekko.persistence.r2dbc.StateSettings
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.slf4j.LoggerFactory

trait TestDbLifecycle extends BeforeAndAfterAll { this: Suite =>

  def typedSystem: ActorSystem[_]

  def testConfigPath: String = "pekko.projection.r2dbc"

  lazy val r2dbcProjectionSettings: R2dbcProjectionSettings =
    R2dbcProjectionSettings(typedSystem.settings.config.getConfig(testConfigPath))

  lazy val r2dbcExecutor: R2dbcExecutor = {
    new R2dbcExecutor(
      // making sure that test harness does not initialize connection factory for the plugin that is being tested
      ConnectionFactoryProvider(typedSystem)
        .connectionFactoryFor("test.connection-factory",
          typedSystem.settings.config.getConfig(r2dbcProjectionSettings.useConnectionFactory).atPath(
            "test.connection-factory")),
      LoggerFactory.getLogger(getClass),
      r2dbcProjectionSettings.logDbCallsExceeding)(typedSystem.executionContext, typedSystem)
  }

  lazy val persistenceExt: Persistence = Persistence(typedSystem)

  override protected def beforeAll(): Unit = {
    lazy val journalSettings: JournalSettings =
      new JournalSettings(typedSystem.settings.config.getConfig("pekko.persistence.r2dbc.journal"))
    lazy val stateSettings: StateSettings =
      new StateSettings(typedSystem.settings.config.getConfig("pekko.persistence.r2dbc.state"))
    Await.result(
      r2dbcExecutor.updateOne("beforeAll delete")(
        _.createStatement(s"delete from ${journalSettings.journalTableWithSchema}")),
      10.seconds)
    Await.result(
      r2dbcExecutor.updateOne("beforeAll delete")(
        _.createStatement(s"delete from ${stateSettings.durableStateTableWithSchema}")),
      10.seconds)
    if (r2dbcProjectionSettings.isOffsetTableDefined) {
      Await.result(
        r2dbcExecutor.updateOne("beforeAll delete")(
          _.createStatement(s"delete from ${r2dbcProjectionSettings.offsetTableWithSchema}")),
        10.seconds)
    }
    Await.result(
      r2dbcExecutor.updateOne("beforeAll delete")(
        _.createStatement(s"delete from ${r2dbcProjectionSettings.timestampOffsetTableWithSchema}")),
      10.seconds)
    Await.result(
      r2dbcExecutor.updateOne("beforeAll delete")(
        _.createStatement(s"delete from ${r2dbcProjectionSettings.managementTableWithSchema}")),
      10.seconds)
    super.beforeAll()
  }

}
