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

import java.util.UUID

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.persistence.r2dbc.ConnectionFactoryProvider
import pekko.persistence.r2dbc.Dialect
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.r2dbc.internal.OffsetPidSeqNr
import pekko.projection.r2dbc.internal.R2dbcOffsetStore
import pekko.projection.r2dbc.internal.R2dbcProjectionImpl
import pekko.projection.r2dbc.internal.h2.H2R2dbcOffsetStore
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.r2dbc.spi.R2dbcException
import org.scalatest.wordspec.AnyWordSpecLike

object H2OffsetStoreTablesSpec {

  // independent of the dialect of the other tests, the H2 database runs in the same process
  val config: Config = ConfigFactory
    .parseString(s"""
      h2-spec.connection-factory = $${pekko.persistence.r2dbc.connection-factory}
      h2-spec.connection-factory {
        driver = "h2"
        h2.database = "h2-offset-store-tables-spec-${UUID.randomUUID()}"
      }
      h2-url-spec.connection-factory = $${pekko.persistence.r2dbc.connection-factory}
      h2-url-spec.connection-factory {
        driver = "h2"
        h2.url = "mem:h2-offset-store-tables-url-spec-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
      }
      """)
    .withFallback(ConfigFactory.load())
    .resolve()
}

class H2OffsetStoreTablesSpec
    extends ScalaTestWithActorTestKit(H2OffsetStoreTablesSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  private def settings(overrides: String): R2dbcProjectionSettings =
    R2dbcProjectionSettings(
      ConfigFactory
        .parseString(overrides)
        .withFallback(ConfigFactory.parseString("""
          dialect = "h2"
          use-connection-factory = "h2-spec.connection-factory"
          """))
        .withFallback(system.settings.config.getConfig(R2dbcProjectionSettings.DefaultConfigPath)))

  private def createOffsetStore(projectionId: ProjectionId, settings: R2dbcProjectionSettings): R2dbcOffsetStore = {
    val connectionFactory = R2dbcProjectionImpl.connectionFactory(settings, ConfigFactory.empty())
    val r2dbcExecutor = R2dbcProjectionImpl.createR2dbcExecutor(connectionFactory, settings, ConfigFactory.empty())
    R2dbcProjectionImpl.createOffsetStore(projectionId, None, settings, r2dbcExecutor)
  }

  private def tablesInSchema(schema: String): Set[String] = {
    // the plain connection factory, which doesn't create tables
    val connectionFactory = ConnectionFactoryProvider(system).connectionFactoryFor("h2-spec.connection-factory")
    val executor = R2dbcProjectionImpl.createR2dbcExecutor(connectionFactory, settings(""), ConfigFactory.empty())
    executor
      .select("tables in schema")(
        _.createStatement("SELECT table_name FROM information_schema.tables WHERE table_schema = ?")
          .bind(0, schema.toUpperCase),
        row => row.get("table_name", classOf[String]).toLowerCase)
      .futureValue
      .toSet
  }

  private def genProjectionId() = ProjectionId(UUID.randomUUID().toString, "00")

  "The H2 offset store" must {

    "be created for the h2 dialect" in {
      createOffsetStore(genProjectionId(), settings("")) shouldBe a[H2R2dbcOffsetStore]
      settings("").dialect shouldBe Dialect.H2
    }

    "create the tables of its own schema and table names" in {
      val projectionSettings = settings("""
        offset-store {
          schema = "h2_spec_custom"
          offset-table = "custom_offset"
          timestamp-offset-table = "custom_timestamp_offset"
          management-table = "custom_management"
        }
        """)
      val projectionId = genProjectionId()
      val offsetStore = createOffsetStore(projectionId, projectionSettings)

      offsetStore.saveOffset(OffsetPidSeqNr(1L)).futureValue
      offsetStore.readOffset[Long]().futureValue shouldBe Some(1L)

      tablesInSchema("h2_spec_custom") shouldBe Set("custom_offset", "custom_timestamp_offset", "custom_management")
    }

    "update offsets and management state with MERGE" in {
      val offsetStore = createOffsetStore(genProjectionId(), settings(""))

      offsetStore.saveOffset(OffsetPidSeqNr(1L)).futureValue
      offsetStore.saveOffset(OffsetPidSeqNr(2L)).futureValue
      offsetStore.readOffset[Long]().futureValue shouldBe Some(2L)

      offsetStore.savePaused(paused = true).futureValue
      offsetStore.readManagementState().futureValue shouldBe Some(ManagementState(paused = true))
      offsetStore.savePaused(paused = false).futureValue
      offsetStore.readManagementState().futureValue shouldBe Some(ManagementState(paused = false))
    }

    "not create the offset table when it is not used" in {
      val projectionSettings = settings("""
        offset-store {
          schema = "h2_spec_no_offset_table"
          offset-table = ""
        }
        """)
      val offsetStore = createOffsetStore(genProjectionId(), projectionSettings)

      offsetStore.savePaused(paused = true).futureValue

      tablesInSchema("h2_spec_no_offset_table") shouldBe Set(
        projectionSettings.timestampOffsetTable,
        projectionSettings.managementTable)
    }

    "not create the tables when the connection is configured with a url" in {
      val offsetStore = createOffsetStore(genProjectionId(),
        settings("""
        use-connection-factory = "h2-url-spec.connection-factory"
        """))

      val exc = offsetStore.readManagementState().failed.futureValue
      exc shouldBe a[R2dbcException]
      withClue(exc.toString) {
        TestDbLifecycle.isH2TableNotFound(exc.asInstanceOf[R2dbcException]) shouldBe true
      }
    }
  }
}
