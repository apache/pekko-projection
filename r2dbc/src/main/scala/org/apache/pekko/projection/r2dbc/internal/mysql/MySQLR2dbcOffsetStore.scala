/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pekko.projection.r2dbc.internal.mysql

import java.time.Clock
import java.time.Instant

import scala.collection.immutable
import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.persistence.r2dbc.internal.Sql.DialectInterpolation
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionId
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.R2dbcOffsetStore
import io.r2dbc.spi.Connection

/**
 * INTERNAL API
 */
@InternalApi
private[projection] object MySQLR2dbcOffsetStore {

  /**
   * Builds the SQL string for deleting old timestamp offsets. When `notInCount` is 0 the plain
   * deletion query (no exclusion list) is returned. When `notInCount > 0` the query uses
   * `CONCAT(persistence_id, '-', seq_nr) NOT IN (?, …)` — note the placement of `NOT IN` rather
   * than `NOT CONCAT(…) IN`, which is invalid SQL.
   */
  private[projection] def buildDeleteOldTimestampOffsetsSql(tableName: String, notInCount: Int): String = {
    if (notInCount == 0) {
      s"DELETE FROM $tableName WHERE slice BETWEEN ? AND ? AND projection_name = ? AND timestamp_offset < ?"
    } else {
      val placeholders = Seq.fill(notInCount)("?").mkString(", ")
      s"DELETE FROM $tableName WHERE slice BETWEEN ? AND ? AND projection_name = ? AND timestamp_offset < ? AND CONCAT(persistence_id, '-', seq_nr) NOT IN ($placeholders)"
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[projection] class MySQLR2dbcOffsetStore(
    projectionId: ProjectionId,
    sourceProvider: Option[BySlicesSourceProvider],
    system: ActorSystem[_],
    settings: R2dbcProjectionSettings,
    r2dbcExecutor: R2dbcExecutor,
    clock: Clock = Clock.systemUTC())
    extends R2dbcOffsetStore(projectionId, sourceProvider, system, settings, r2dbcExecutor, clock) {

  override lazy val timestampSql: String = "NOW(6)"

  override val upsertOffsetSql: String = sql"""
    INSERT INTO $offsetTable
    (projection_name, projection_key, current_offset, manifest, mergeable, last_updated)
    VALUES (?,?,?,?,?,?) AS excluded
    ON DUPLICATE KEY UPDATE
    current_offset = excluded.current_offset,
    manifest = excluded.manifest,
    mergeable = excluded.mergeable,
    last_updated = excluded.last_updated"""

  override val updateManagementStateSql: String = sql"""
    INSERT INTO $managementTable
    (projection_name, projection_key, paused, last_updated)
    VALUES (?,?,?,?) AS excluded
    ON DUPLICATE KEY UPDATE
    paused = excluded.paused,
    last_updated = excluded.last_updated"""

  /**
   * MySQL's r2dbc driver validates that all parameters are bound before `add()` is called
   * on a batch statement, unlike PostgreSQL's driver. We therefore bind the first record
   * before folding over the remaining records with `add()`.
   */
  override protected def insertTimestampOffsetInTx(
      conn: Connection,
      records: immutable.IndexedSeq[R2dbcOffsetStore.Record]): Future[Long] = {
    require(records.nonEmpty)

    logger.trace("saving timestamp offset [{}], {}", records.last.timestamp, records)

    val statement = conn.createStatement(insertTimestampOffsetSql)

    if (records.size == 1) {
      val boundStatement = bindTimestampOffsetRecord(statement, records.head)
      R2dbcExecutor.updateOneInTx(boundStatement)
    } else {
      // Bind the first record before calling add() for the rest; MySQL validates all parameters
      // are bound on the current batch row before accepting add().
      val boundStatement =
        records.tail.foldLeft(bindTimestampOffsetRecord(statement, records.head)) { (stmt, rec) =>
          stmt.add()
          bindTimestampOffsetRecord(stmt, rec)
        }
      R2dbcExecutor.updateBatchInTx(boundStatement)
    }
  }

  /**
   * MySQL does not support `= ANY (?)` with an array parameter or the `||` string concatenation
   * operator. This override builds the DELETE SQL dynamically using `CONCAT()` and
   * `NOT IN (?, ?, ...)` with one placeholder per exclusion entry.
   */
  override protected def executeDeleteOldTimestampOffsets(
      minSlice: Int,
      maxSlice: Int,
      until: Instant,
      notInLatestBySlice: Array[String]): Future[Long] = {
    r2dbcExecutor.updateOne("delete old timestamp offset") { conn =>
      val stmt = if (notInLatestBySlice.isEmpty) {
        conn
          .createStatement(
            MySQLR2dbcOffsetStore.buildDeleteOldTimestampOffsetsSql(timestampOffsetTable, 0))
          .bind(0, minSlice)
          .bind(1, maxSlice)
          .bind(2, projectionId.name)
          .bind(3, until)
      } else {
        val s = conn
          .createStatement(
            MySQLR2dbcOffsetStore.buildDeleteOldTimestampOffsetsSql(
              timestampOffsetTable,
              notInLatestBySlice.length))
          .bind(0, minSlice)
          .bind(1, maxSlice)
          .bind(2, projectionId.name)
          .bind(3, until)
        notInLatestBySlice.zipWithIndex.foldLeft(s) { case (st, (value, idx)) =>
          st.bind(4 + idx, value)
        }
      }
      stmt
    }
  }
}
