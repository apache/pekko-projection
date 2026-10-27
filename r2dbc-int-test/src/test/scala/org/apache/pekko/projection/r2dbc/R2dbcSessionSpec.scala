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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorSystem
import pekko.projection.r2dbc.javadsl
import pekko.projection.r2dbc.scaladsl
import org.scalatest.wordspec.AnyWordSpecLike

object R2dbcSessionSpec {
  val table = "r2dbc_session_spec"
}

class R2dbcSessionSpec
    extends ScalaTestWithActorTestKit(TestConfig.config)
    with AnyWordSpecLike
    with TestDbLifecycle
    with LogCapturing {
  import R2dbcSessionSpec.table

  override def typedSystem: ActorSystem[?] = system
  private implicit val ec: ExecutionContext = system.executionContext

  private val settings = R2dbcProjectionSettings(testKit.system)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(
      r2dbcExecutor.executeDdl("beforeAll createTable") { conn =>
        conn.createStatement(
          s"CREATE TABLE IF NOT EXISTS $table (id VARCHAR(255) NOT NULL, PRIMARY KEY(id))")
      },
      10.seconds)
    Await.result(r2dbcExecutor.updateOne("beforeAll delete")(_.createStatement(s"DELETE FROM $table")), 10.seconds)
  }

  private def insertSql(id: String): String = s"INSERT INTO $table (id) VALUES ('$id')"

  private def exists(id: String): Boolean =
    scaladsl.R2dbcSession
      .withSession(system, settings.useConnectionFactory) { session =>
        session.selectOne(session.createStatement(s"SELECT id FROM $table WHERE id = '$id'"))(
          _.get("id", classOf[String]))
      }
      .futureValue
      .isDefined

  "scaladsl R2dbcSession.withSession" must {

    "commit the transaction when the returned Future succeeds" in {
      scaladsl.R2dbcSession
        .withSession(system) { session =>
          session.updateOne(session.createStatement(insertSql("scala-commit")))
        }
        .futureValue shouldBe 1L

      exists("scala-commit") shouldBe true
    }

    "use the given connection factory config path" in {
      scaladsl.R2dbcSession
        .withSession(system, settings.useConnectionFactory) { session =>
          session.updateOne(session.createStatement(insertSql("scala-path")))
        }
        .futureValue shouldBe 1L

      exists("scala-path") shouldBe true
    }

    "roll back the transaction when the returned Future fails" in {
      val result = scaladsl.R2dbcSession.withSession(system) { session =>
        session
          .updateOne(session.createStatement(insertSql("scala-rollback")))
          .flatMap(_ => Future.failed(new RuntimeException("boom")))
      }
      result.failed.futureValue.getMessage shouldBe "boom"

      exists("scala-rollback") shouldBe false
    }

    "fail the returned Future for an unknown connection factory config path" in {
      // not thrown from withSession, and the function is not run
      val result = scaladsl.R2dbcSession.withSession(system, "pekko.projection.r2dbc.no-such-connection-factory") {
        _ => fail("function must not be run")
      }
      result.failed.futureValue shouldBe a[com.typesafe.config.ConfigException]
    }
  }

  "javadsl R2dbcSession.withSession" must {

    "commit the transaction when the returned CompletionStage succeeds" in {
      javadsl.R2dbcSession
        .withSession(
          system,
          (session: javadsl.R2dbcSession) => session.updateOne(session.createStatement(insertSql("java-commit"))))
        .asScala
        .futureValue shouldBe 1L

      exists("java-commit") shouldBe true
    }

    "roll back the transaction when the returned CompletionStage fails" in {
      val result = javadsl.R2dbcSession.withSession(
        system,
        settings.useConnectionFactory,
        (session: javadsl.R2dbcSession) =>
          session
            .updateOne(session.createStatement(insertSql("java-rollback")))
            .thenCompose(_ => CompletableFuture.failedFuture[java.lang.Long](new RuntimeException("boom"))))
      val failure = result.asScala.failed.futureValue match {
        case e: CompletionException => e.getCause
        case e                      => e
      }
      failure.getMessage shouldBe "boom"

      exists("java-rollback") shouldBe false
    }

    "fail the returned CompletionStage for an unknown connection factory config path" in {
      val result = javadsl.R2dbcSession.withSession[String](
        system,
        "pekko.projection.r2dbc.no-such-connection-factory",
        (_: javadsl.R2dbcSession) => fail("function must not be run"))
      result.asScala.failed.futureValue shouldBe a[com.typesafe.config.ConfigException]
    }
  }

}
