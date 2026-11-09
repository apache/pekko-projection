/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.projection.r2dbc

import java.time.Instant
import java.util.UUID

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.Persistence
import pekko.persistence.query.TimestampOffset
import pekko.persistence.r2dbc.Dialect
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.r2dbc.R2dbcTimestampOffsetStoreSpec.TestTimestampSourceProvider
import pekko.projection.r2dbc.internal.OffsetPidSeqNr
import pekko.projection.r2dbc.internal.R2dbcOffsetStore
import pekko.projection.r2dbc.internal.R2dbcProjectionImpl
import pekko.projection.r2dbc.internal.mariadb.MariaDBR2dbcOffsetStore
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class MariaDBR2dbcOffsetStoreSpec
    extends ScalaTestWithActorTestKit(TestConfig.config)
    with AnyWordSpecLike
    with LogCapturing {

  private val settings = R2dbcProjectionSettings(system)

  private def assumeMariaDB(): Unit =
    assume(settings.dialect == Dialect.MariaDB, "requires a MariaDB database, run with the mariadb dialect")

  private def createR2dbcExecutor(settings: R2dbcProjectionSettings): R2dbcExecutor =
    R2dbcProjectionImpl.createR2dbcExecutor(
      R2dbcProjectionImpl.connectionFactory(settings, ConfigFactory.empty()),
      settings,
      ConfigFactory.empty())

  private def createOffsetStore(projectionId: ProjectionId, settings: R2dbcProjectionSettings): R2dbcOffsetStore = {
    val r2dbcExecutor = createR2dbcExecutor(settings)
    val sourceProvider =
      new TestTimestampSourceProvider(0, Persistence(system).numberOfSlices - 1, TestClock.nowMicros())
    R2dbcOffsetStore.fromConfig(projectionId, Some(sourceProvider), system, settings, r2dbcExecutor)
  }

  private def genProjectionId() = ProjectionId(UUID.randomUUID().toString, "00")

  "The MariaDB offset store" must {

    "be created for the mariadb dialect, with upserts that don't use the MySQL row alias" in {
      // no database calls, so this runs with every dialect
      val offsetStore = createOffsetStore(genProjectionId(), settings.withDialect(Dialect.MariaDB)) match {
        case mariaDB: MariaDBR2dbcOffsetStore => mariaDB
        case other                            => fail(s"Expected MariaDBR2dbcOffsetStore, but was [${other.getClass.getName}]")
      }

      (offsetStore.upsertOffsetSql should not).include("AS excluded")
      offsetStore.upsertOffsetSql should include("current_offset = VALUES(current_offset)")
      (offsetStore.updateManagementStateSql should not).include("AS excluded")
      offsetStore.updateManagementStateSql should include("paused = VALUES(paused)")
    }

    "update offsets and management state" in {
      assumeMariaDB()
      val offsetStore = createOffsetStore(genProjectionId(), settings)

      offsetStore.saveOffset(OffsetPidSeqNr(1L)).futureValue
      offsetStore.saveOffset(OffsetPidSeqNr(2L)).futureValue
      offsetStore.readOffset[Long]().futureValue shouldBe Some(2L)

      offsetStore.savePaused(paused = true).futureValue
      offsetStore.readManagementState().futureValue shouldBe Some(ManagementState(paused = true))
      offsetStore.savePaused(paused = false).futureValue
      offsetStore.readManagementState().futureValue shouldBe Some(ManagementState(paused = false))
    }

    "store timestamp offsets as the same instant, independent of the time zone of the server" in {
      assumeMariaDB()
      // The MariaDB container of the tests runs in America/New_York. Instants are bound as their UTC local time,
      // which the server interprets in the session time zone. With a New York session, 01:30 on 2026-11-01 would be
      // stored 5 hours later and still read back unchanged, so the stored instant is checked with UNIX_TIMESTAMP,
      // which doesn't depend on the session time zone. 02:30 on 2027-03-14 doesn't exist in New York, the server
      // would reject it.
      val r2dbcExecutor = createR2dbcExecutor(settings)
      val timestamps = Seq(Instant.parse("2026-11-01T01:30:00.123456Z"), Instant.parse("2027-03-14T02:30:00.654321Z"))

      timestamps.zipWithIndex.foreach { case (timestamp, i) =>
        val projectionId = genProjectionId()
        val offsetStore = createOffsetStore(projectionId, settings)
        val offset = TimestampOffset(timestamp, Map("p1" -> (i + 1L)))
        offsetStore.saveOffset(OffsetPidSeqNr(offset, "p1", i + 1L)).futureValue
        withClue(s"timestamp [$timestamp]: ") {
          val storedEpochSeconds = r2dbcExecutor
            .selectOne("select stored timestamp offset")(
              _.createStatement(
                s"SELECT UNIX_TIMESTAMP(timestamp_offset) AS epoch_seconds FROM ${settings.timestampOffsetTableWithSchema} " +
                "WHERE projection_name = ?")
                .bind(0, projectionId.name),
              row => row.get("epoch_seconds", classOf[java.math.BigDecimal]))
            .futureValue
          storedEpochSeconds.map(BigDecimal(_)) shouldBe Some(
            BigDecimal(timestamp.getEpochSecond) + BigDecimal(timestamp.getNano) / 1000000000)

          // a new offset store reads the offset from the database
          createOffsetStore(projectionId, settings).readOffset[TimestampOffset]().futureValue shouldBe Some(
            offset)
        }
      }
    }
  }
}
