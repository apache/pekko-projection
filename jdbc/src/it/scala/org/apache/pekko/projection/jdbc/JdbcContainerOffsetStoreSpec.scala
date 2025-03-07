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

package org.apache.pekko.projection.jdbc

import java.sql.DriverManager

import scala.language.existentials

import org.apache.pekko
import pekko.projection.TestTags
import pekko.projection.jdbc.JdbcOffsetStoreSpec.JdbcSpecConfig
import pekko.projection.jdbc.JdbcOffsetStoreSpec.PureJdbcSession
import pekko.projection.jdbc.internal.Dialect
import pekko.projection.jdbc.internal.OracleDialect
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.Tag
import org.testcontainers.containers._
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy

object JdbcContainerOffsetStoreSpec {

  abstract class ContainerJdbcSpecConfig(dialect: String) extends JdbcSpecConfig {

    val tag: Tag = TestTags.ContainerDb

    final val schemaName = "test_schema"

    override def config: Config =
      baseConfig.withFallback(ConfigFactory.parseString(s"""
        pekko.projection.jdbc {
          offset-store.schema = "$schemaName"
          dialect = $dialect
        }
        """))

    def jdbcSessionFactory(): PureJdbcSession = {

      // this is safe as tests only start after the container is init
      val container = _container.get

      new PureJdbcSession(() => {
        Class.forName(container.getDriverClassName)
        val conn =
          DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
        conn.setAutoCommit(false)
        conn
      })
    }

    protected var _container: Option[JdbcDatabaseContainer[_]] = None

    def newContainer(): JdbcDatabaseContainer[_]

    override def initContainer(): Unit = {
      val container = newContainer()
      _container = Some(container)
      container.withStartupCheckStrategy(new IsRunningStartupCheckStrategy)
      container.withStartupAttempts(5)
      container.start()
    }

    override def stopContainer(): Unit =
      _container.get.stop()
  }

  object PostgresSpecConfig extends ContainerJdbcSpecConfig("postgres-dialect") {
    val name = "Postgres Database"
    override def newContainer(): JdbcDatabaseContainer[_] =
      new PostgreSQLContainer("postgres:13.1").withInitScript("db/default-init.sql")
  }
  object PostgresLegacySchemaSpecConfig extends ContainerJdbcSpecConfig("postgres-dialect") {
    val name = "Postgres Database"

    override def config: Config =
      super.config.withFallback(ConfigFactory.parseString("""
        pekko.projection.jdbc = {
           offset-store.use-lowercase-schema = false
        }
        """))

    override def newContainer(): JdbcDatabaseContainer[_] =
      new PostgreSQLContainer("postgres:13.1").withInitScript("db/default-init.sql")
  }

  object MySQLSpecConfig extends ContainerJdbcSpecConfig("mysql-dialect") {
    val name = "MySQL Database"
    override def newContainer(): JdbcDatabaseContainer[_] =
      new MySQLContainer("mysql:8.0.22").withDatabaseName(schemaName)
  }

  object MSSQLServerSpecConfig extends ContainerJdbcSpecConfig("mssql-dialect") {
    val name = "MS SQL Server Database"
    override val tag: Tag = TestTags.FlakyDb
    override def newContainer(): JdbcDatabaseContainer[_] = {
      val container: MSSQLServerContainer[_] =
        new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2019-CU32-ubuntu-20.04")
      container.acceptLicense()
      container.withInitScript("db/default-init.sql")
      container.withUrlParam("integratedSecurity", "false")
      container.withUrlParam("encrypt", "false")
      container
    }
  }

  object OracleSpecConfig extends ContainerJdbcSpecConfig("oracle-dialect") {
    val name = "Oracle Database"

    // related to https://github.com/testcontainers/testcontainers-java/issues/2313
    // otherwise we get ORA-01882: timezone region not found
    System.setProperty("oracle.jdbc.timezoneAsRegion", "false")
    override def newContainer() =
      new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
        .withUsername("TEST_SCHEMA")
        .withPassword("password")
  }

}

class PostgresJdbcOffsetStoreSpec extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.PostgresSpecConfig)

class PostgresJdbcOffsetStoreLegacySchemaSpec
    extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.PostgresLegacySchemaSpecConfig) {

  private val table = settings.schema.map(s => s""""$s"."${settings.table}"""").getOrElse(s""""${settings.table}"""")
  override def selectLastStatement: String =
    s"""SELECT * FROM $table WHERE "PROJECTION_NAME" = ? AND "PROJECTION_KEY" = ?"""
}

class MySQLJdbcOffsetStoreSpec extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.MySQLSpecConfig) {
  override def selectLastStatement: String = Dialect.removeQuotes(super.selectLastStatement)
}

class MSSQLServerJdbcOffsetStoreSpec extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.MSSQLServerSpecConfig)

class OracleJdbcOffsetStoreSpec extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.OracleSpecConfig) {

  private val dialect: Dialect = OracleDialect(settings.schema, settings.table, settings.managementTable)

  private val table =
    dialect.schema.map(s => s""""$s"."${dialect.tableName}"""").getOrElse(s""""${dialect.tableName}"""")
  override def selectLastStatement: String =
    s"""SELECT * FROM $table WHERE "PROJECTION_NAME" = ? AND "PROJECTION_KEY" = ?"""
}
