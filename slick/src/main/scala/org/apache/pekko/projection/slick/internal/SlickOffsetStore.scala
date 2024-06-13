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

package org.apache.pekko.projection.slick.internal

import java.time.Clock

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.projection.MergeableOffset
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.internal.OffsetSerialization
import pekko.projection.jdbc.internal.Dialect
import pekko.projection.jdbc.internal.H2Dialect
import pekko.projection.jdbc.internal.JdbcSessionUtil
import pekko.projection.jdbc.internal.MSSQLServerDialect
import pekko.projection.jdbc.internal.MySQLDialect
import pekko.projection.jdbc.internal.OracleDialect
import pekko.projection.jdbc.internal.PostgresDialect
import pekko.util.Helpers.toRootLowerCase
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

/**
 * INTERNAL API
 */
@InternalApi private[projection] class SlickOffsetStore[P <: JdbcProfile](
    system: ActorSystem[_],
    databaseConfig: DatabaseConfig[P],
    slickSettings: SlickSettings,
    clock: Clock) {

  def this(system: ActorSystem[_], databaseConfig: DatabaseConfig[P], slickSettings: SlickSettings) =
    this(system, databaseConfig, slickSettings, Clock.systemUTC())

  private[projection] val profile: P = databaseConfig.profile

  import profile.api._
  import OffsetSerialization.MultipleOffsets
  import OffsetSerialization.SingleOffset

  val (dialect, useLowerCase): (Dialect, Boolean) = {

    val useLowerCase = slickSettings.useLowerCase

    profile match {
      case _: slick.jdbc.H2Profile =>
        (
          H2Dialect(slickSettings.schema, slickSettings.table, slickSettings.managementTable, useLowerCase),
          useLowerCase)
      case _: slick.jdbc.PostgresProfile =>
        (
          PostgresDialect(slickSettings.schema, slickSettings.table, slickSettings.managementTable, useLowerCase),
          useLowerCase)
      // mysql and Sql server are case insensitive, we favor lower case
      case _: slick.jdbc.SQLServerProfile =>
        (MSSQLServerDialect(slickSettings.schema, slickSettings.table, slickSettings.managementTable), true)
      case _: slick.jdbc.MySQLProfile =>
        (MySQLDialect(slickSettings.schema, slickSettings.table, slickSettings.managementTable), true)
      // oracle must always use quoted + uppercase
      case _: slick.jdbc.OracleProfile =>
        (OracleDialect(slickSettings.schema, slickSettings.table, slickSettings.managementTable), false)

    }
  }

  private val offsetSerialization = new OffsetSerialization(system)
  import offsetSerialization.fromStorageRepresentation
  import offsetSerialization.toStorageRepresentation

  def readOffset[Offset](projectionId: ProjectionId)(implicit ec: ExecutionContext): Future[Option[Offset]] = {
    val action = offsetTable.filter(_.projectionName === projectionId.name).result.map { maybeRow =>
      maybeRow.map(row =>
        SingleOffset(ProjectionId(projectionId.name, row.projectionKey), row.manifest, row.offsetStr, row.mergeable))
    }

    val results = databaseConfig.db.run(action)

    results.map {
      case Nil => None
      case reps if reps.forall(_.mergeable) =>
        Some(fromStorageRepresentation[MergeableOffset[_], Offset](MultipleOffsets(reps.toList)).asInstanceOf[Offset])
      case reps =>
        reps.find(_.id == projectionId).map(fromStorageRepresentation[Offset, Offset])
    }
  }

  private def newRow[Offset](rep: SingleOffset, millisSinceEpoch: Long): DBIO[_] =
    offsetTable.insertOrUpdate(
      OffsetRow(rep.id.name, rep.id.key, rep.offsetStr, rep.manifest, rep.mergeable, millisSinceEpoch))

  def saveOffset[Offset](projectionId: ProjectionId, offset: Offset): slick.dbio.DBIO[_] = {
    val millisSinceEpoch = clock.instant().toEpochMilli
    toStorageRepresentation(projectionId, offset) match {
      case offset: SingleOffset => newRow(offset, millisSinceEpoch)
      case MultipleOffsets(reps) =>
        val actions = reps.map(rep => newRow(rep, millisSinceEpoch))
        DBIO.sequence(actions)
    }
  }

  def clearOffset(projectionId: ProjectionId): slick.dbio.DBIO[_] = {
    offsetTable.filter(row => row.projectionName === projectionId.name && row.projectionKey === projectionId.key).delete
  }

  private def adaptCase(str: String): String =
    if (useLowerCase) toRootLowerCase(str)
    else str

  class OffsetStoreTable(tag: Tag) extends Table[OffsetRow](tag, dialect.schema, dialect.tableName) {

    def projectionName = column[String](adaptCase("PROJECTION_NAME"), O.Length(255))
    def projectionKey = column[String](adaptCase("PROJECTION_KEY"), O.Length(255))
    def offset = column[String](adaptCase("CURRENT_OFFSET"), O.Length(255))
    def manifest = column[String](adaptCase("MANIFEST"), O.Length(4))
    def mergeable = column[Boolean](adaptCase("MERGEABLE"))
    def lastUpdated = column[Long](adaptCase("LAST_UPDATED"))

    def pk = primaryKey(adaptCase("PK_PROJECTION_ID"), (projectionName, projectionKey))
    def idx = index(adaptCase("PROJECTION_NAME_INDEX"), projectionName)

    def * = (projectionName, projectionKey, offset, manifest, mergeable, lastUpdated).mapTo[OffsetRow]
  }

  case class OffsetRow(
      projectionName: String,
      projectionKey: String,
      offsetStr: String,
      manifest: String,
      mergeable: Boolean,
      lastUpdated: Long)

  val offsetTable = TableQuery[OffsetStoreTable]

  case class ManagementStateRow(projectionName: String, projectionKey: String, paused: Boolean, lastUpdated: Long)

  class ManagementTable(tag: Tag) extends Table[ManagementStateRow](tag, dialect.schema, dialect.managementTableName) {

    def projectionName = column[String](adaptCase("PROJECTION_NAME"), O.Length(255))
    def projectionKey = column[String](adaptCase("PROJECTION_KEY"), O.Length(255))
    def paused = column[Boolean](adaptCase("PAUSED"))
    def lastUpdated = column[Long](adaptCase("LAST_UPDATED"))

    def pk = primaryKey(adaptCase("PK_PROJECTION_MANAGEMENT_ID"), (projectionName, projectionKey))

    def * = (projectionName, projectionKey, paused, lastUpdated).mapTo[ManagementStateRow]
  }

  val managementTable = TableQuery[ManagementTable]

  def createIfNotExists(): Future[Done] = {
    val prepareSchemaDBIO = SimpleDBIO[Unit] { jdbcContext =>
      val connection = jdbcContext.connection
      dialect.createTableStatements.foreach(sql =>
        JdbcSessionUtil.tryWithResource(connection.createStatement()) { stmt =>
          stmt.execute(sql)
        })
    }
    val prepareManagementSchemaDBIO = SimpleDBIO[Unit] { jdbcContext =>
      val connection = jdbcContext.connection
      dialect.createManagementTableStatements.foreach(sql =>
        JdbcSessionUtil.tryWithResource(connection.createStatement()) { stmt =>
          stmt.execute(sql)
        })
    }
    databaseConfig.db.run(DBIO.seq(prepareSchemaDBIO, prepareManagementSchemaDBIO))
      .map(_ => Done)(ExecutionContexts.parasitic)
  }

  def dropIfExists(): Future[Done] = {
    val prepareSchemaDBIO = SimpleDBIO[Unit] { jdbcContext =>
      val connection = jdbcContext.connection
      JdbcSessionUtil.tryWithResource(connection.createStatement()) { stmt =>
        stmt.execute(dialect.dropTableStatement)
      }
    }
    val prepareManagementSchemaDBIO = SimpleDBIO[Unit] { jdbcContext =>
      val connection = jdbcContext.connection
      JdbcSessionUtil.tryWithResource(connection.createStatement()) { stmt =>
        stmt.execute(dialect.dropManagementTableStatement)
      }
    }
    databaseConfig.db.run(DBIO.seq(prepareSchemaDBIO, prepareManagementSchemaDBIO))
      .map(_ => Done)(ExecutionContexts.parasitic)
  }

  def readManagementState(projectionId: ProjectionId)(
      implicit ec: ExecutionContext): Future[Option[ManagementState]] = {
    val action = managementTable
      .filter(row => row.projectionName === projectionId.name && row.projectionKey === projectionId.key)
      .result
      .headOption
      .map { maybeRow =>
        maybeRow.map(row => ManagementState(row.paused))
      }

    databaseConfig.db.run(action)
  }

  def savePaused(projectionId: ProjectionId, paused: Boolean): Future[Done] = {
    val millisSinceEpoch = clock.instant().toEpochMilli
    val action =
      managementTable.insertOrUpdate(ManagementStateRow(projectionId.name, projectionId.key, paused, millisSinceEpoch))

    databaseConfig.db.run(action).map(_ => Done)(ExecutionContexts.parasitic)
  }
}
