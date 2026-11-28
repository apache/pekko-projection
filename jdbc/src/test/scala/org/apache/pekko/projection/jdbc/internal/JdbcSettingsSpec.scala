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

package org.apache.pekko.projection.jdbc.internal

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class JdbcSettingsSpec extends TestSuite with Matchers with AnyWordSpecLike with LogCapturing {

  "Loading JdbcSettings" must {

    "accept convert a valid string to integer when reading the pool size" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = "5"
            }
          }
          """)

      createSystem(config) { sys =>
        JdbcSettings(sys)
      }
    }

    "accept convert a valid string (with spaces) to integer when reading the pool size" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = " 5  "
            }
          }
          """)

      createSystem(config) { sys =>
        JdbcSettings(sys)
      }
    }

    "accept a correctly filled pool size" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = 5
            }
          }
          """)

      createSystem(config) { sys =>
        JdbcSettings(sys)
      }
    }

    "throw an exception if pool size is configured with invalid value (unparsable string)" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = "this-is-not-valid"
            }
          }
          """)

      createSystem(config) { sys =>
        val msg =
          intercept[IllegalArgumentException] {
            JdbcSettings(sys)
          }.getMessage

        msg should startWith(
          """Value ["this-is-not-valid"] is not a valid value for settings 'pekko.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size'""")
      }
    }

    "throw exception when dialect not defined" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = 5
            }
          }
          """)

      createSystem(config) { sys =>
        val msg =
          intercept[IllegalArgumentException] {
            JdbcSettings(sys)
          }.getMessage

        msg shouldBe "Dialect type not set. Settings 'pekko.projection.jdbc.dialect' currently set to []"
      }
    }

    "throw exception when dialect is unknown" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h3-dialect"
              blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = 5
            }
          }
          """)

      createSystem(config) { sys =>
        val msg =
          intercept[IllegalArgumentException] {
            JdbcSettings(sys)
          }.getMessage

        msg shouldBe "Unknown dialect type: [h3-dialect]. Check settings 'pekko.projection.jdbc.dialect'"
      }
    }

    "throw exception when pool size not defined" in {

      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
            }
          }
          """)

      createSystem(config) { sys =>
        intercept[IllegalArgumentException] {
          JdbcSettings(sys)
        }
      }
    }

    "dont' fail if user decide to step out of a thread-pool-executor" in {
      val config: Config =
        ConfigFactory.parseString("""
          pekko {
            loglevel = "DEBUG"
            projection.jdbc {
              dialect = "h2-dialect"
              
              # user tweaks the dispatcher to be something else
              # we can't check the pool size if it's not a thread-pool-executor
              blocking-jdbc-dispatcher {
                executor = "fork-join-executor"
                fork-join-executor {
                  parallelism-min = 2
                  parallelism-factor = 2.0
                  parallelism-max = 10
                }
                throughput = 1
              }
            }
          }
          """)

      createSystem(config) { sys =>
        JdbcSettings(sys)
      }

    }
  }

  "PostgreSQL offset index naming" must {
    def settings(
        table: String = "offsets",
        schema: String = "",
        lowerCase: Boolean = true,
        perTable: Boolean = false,
        dialect: String = "postgres-dialect"): JdbcSettings = {
      val config = ConfigFactory.load().getConfig("pekko.projection.jdbc")
        .withValue("dialect", ConfigValueFactory.fromAnyRef(dialect))
        .withValue("offset-store.table", ConfigValueFactory.fromAnyRef(table))
        .withValue("offset-store.schema", ConfigValueFactory.fromAnyRef(schema))
        .withValue("offset-store.use-lowercase-schema", ConfigValueFactory.fromAnyRef(Boolean.box(lowerCase)))
        .withValue("offset-store.postgres-table-specific-index", ConfigValueFactory.fromAnyRef(Boolean.box(perTable)))
      JdbcSettings(config, ExecutionContext.parasitic)
    }

    def indexStatement(value: JdbcSettings): String = value.createOffsetTableStatements.last

    def indexName(value: JdbcSettings): String = indexStatement(value).split(" ")(5).stripPrefix("\"").stripSuffix("\"")

    "keep the historical name by default and for other dialects" in {
      val config = ConfigFactory.load().getConfig("pekko.projection.jdbc")
        .withValue("dialect", ConfigValueFactory.fromAnyRef("postgres-dialect"))
      val default = JdbcSettings(config, ExecutionContext.parasitic)
      val legacy = settings()
      val h2 = settings(perTable = true, dialect = "h2-dialect")
      indexName(default) shouldBe "projection_name_index"
      indexName(legacy) shouldBe "projection_name_index"
      indexName(h2) shouldBe "projection_name_index"
    }

    "give two custom tables in the same schema distinct bounded and stable index names" in {
      val sharedPrefix = "orders_" + "x" * 55
      val first = settings(sharedPrefix + "a", schema = "analytics", perTable = true)
      val second = settings(sharedPrefix + "b", schema = "analytics", perTable = true)
      val unqualified = settings(sharedPrefix + "a", perTable = true)

      indexName(first) should not be indexName(second)
      indexName(first) shouldBe indexName(unqualified)
      (indexName(first) should fullyMatch).regex("pekko_projection_name_[0-9a-f]{40}")
      indexName(first).length should be <= 63
      indexStatement(first) should include(s"on analytics.${first.table} (projection_name)")
      first.dialect.dropTableStatement shouldBe s"drop table if exists analytics.${first.table};"
    }

    "follow the dialect's folding rules and retain the target table's case when quoted" in {
      val lower = settings("dailyorders", perTable = true)
      val folded = settings("DailyOrders", perTable = true)
      val quoted = settings("DailyOrders", schema = "Analytics", lowerCase = false, perTable = true)

      indexName(folded) shouldBe indexName(lower)
      indexName(quoted) should not be indexName(lower)
      indexStatement(quoted) should endWith("""on "Analytics"."DailyOrders" ("PROJECTION_NAME");""")
    }

    "reject names PostgreSQL would truncate when opting in" in {
      val truncated = settings("é" * 32, lowerCase = false, perTable = true)
      intercept[IllegalArgumentException] {
        truncated.createOffsetTableStatements
      }.getMessage should include("63 UTF-8 bytes")
    }
  }

  private def createSystem(config: Config)(func: ActorSystem[Any] => Unit) = {
    val sys = ActorSystem(Behaviors.empty[Any], "test-sys", config)
    func(sys)
    sys.terminate()
    Await.ready(sys.whenTerminated, 3.seconds)
  }
}
