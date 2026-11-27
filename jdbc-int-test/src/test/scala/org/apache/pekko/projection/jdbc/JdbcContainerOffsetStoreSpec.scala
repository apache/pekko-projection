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
import pekko.projection.jdbc.internal.JdbcOffsetStore
import pekko.projection.jdbc.internal.JdbcSessionUtil.withConnection
import pekko.projection.jdbc.internal.OracleDialect
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.scalatest.Tag
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.OracleContainer
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy
import org.testcontainers.mssqlserver.MSSQLServerContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer

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

    protected var _container: Option[JdbcDatabaseContainer[?]] = None

    def newContainer(): JdbcDatabaseContainer[?]

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
    override def newContainer(): JdbcDatabaseContainer[?] =
      new PostgreSQLContainer("postgres:18.0").withInitScript("db/default-init.sql")
  }
  object PostgresLegacySchemaSpecConfig extends ContainerJdbcSpecConfig("postgres-dialect") {
    val name = "Postgres Database"

    override def config: Config =
      super.config.withFallback(ConfigFactory.parseString("""
        pekko.projection.jdbc = {
           offset-store.use-lowercase-schema = false
        }
        """))

    override def newContainer(): JdbcDatabaseContainer[?] =
      new PostgreSQLContainer("postgres:18.0").withInitScript("db/default-init.sql")
  }

  object MySQLSpecConfig extends ContainerJdbcSpecConfig("mysql-dialect") {
    val name = "MySQL Database"
    override def newContainer(): JdbcDatabaseContainer[?] =
      new MySQLContainer("mysql:9.5.0").withDatabaseName(schemaName)
  }

  object MSSQLServerSpecConfig extends ContainerJdbcSpecConfig("mssql-dialect") {
    val name = "MS SQL Server Database"
    override val tag: Tag = TestTags.FlakyDb
    override def newContainer(): JdbcDatabaseContainer[?] = {
      val container =
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

class PostgresJdbcOffsetStoreSpec extends JdbcOffsetStoreSpec(JdbcContainerOffsetStoreSpec.PostgresSpecConfig) {

  private val postgres = JdbcContainerOffsetStoreSpec.PostgresSpecConfig

  "PostgreSQL table-specific index names" should {
    "index both co-located offset tables and keep the second index when the first table is dropped" taggedAs
    (postgres.tag) in {
      val tables = List("projection_orders", "projection_products")
      val stores = tables.map { table =>
        val config = settings.config
          .withValue("offset-store.table", ConfigValueFactory.fromAnyRef(table))
          .withValue("offset-store.management-table", ConfigValueFactory.fromAnyRef(s"${table}_management"))
          .withValue("offset-store.postgres-table-specific-index", ConfigValueFactory.fromAnyRef(Boolean.box(true)))
        new JdbcOffsetStore(system, settings.copy(config = config), () => postgres.jdbcSessionFactory())
      }

      def projectionNameIndexes(table: String): Set[String] =
        withConnection(() => postgres.jdbcSessionFactory()) { conn =>
          val indexes = conn.getMetaData.getIndexInfo(null, postgres.schemaName, table, false, false)
          try {
            val names = Set.newBuilder[String]
            while (indexes.next()) {
              if ("projection_name" == indexes.getString("COLUMN_NAME") && indexes.getBoolean("NON_UNIQUE"))
                names += indexes.getString("INDEX_NAME")
            }
            names.result()
          } finally indexes.close()
        }(settings.executionContext).futureValue

      val List(first, second) = stores: @unchecked
      try {
        first.createIfNotExists().futureValue
        second.createIfNotExists().futureValue
        second.createIfNotExists().futureValue
        val firstIndexes = projectionNameIndexes(tables.head)
        val secondIndexes = projectionNameIndexes(tables.last)
        firstIndexes should have size 1
        secondIndexes should have size 1
        firstIndexes.intersect(secondIndexes) shouldBe empty
        first.dropIfExists().futureValue
        projectionNameIndexes(tables.head) shouldBe empty
        projectionNameIndexes(tables.last) shouldBe secondIndexes
      } finally {
        try first.dropIfExists().futureValue
        finally second.dropIfExists().futureValue
      }
    }
  }
}

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
