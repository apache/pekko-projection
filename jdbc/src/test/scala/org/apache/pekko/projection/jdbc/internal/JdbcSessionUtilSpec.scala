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

package org.apache.pekko.projection.jdbc.internal

import java.sql.SQLException

import scala.collection.mutable.ListBuffer

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class JdbcSessionUtilSpec extends TestSuite with Matchers with AnyWordSpecLike with LogCapturing {

  private class TestResource(onClose: () => Unit = () => ()) extends AutoCloseable {
    var closeCount = 0
    override def close(): Unit = {
      closeCount += 1
      onClose()
    }
  }

  /** Records every resource that gets created, so that we can detect a resource that is created but never closed. */
  private class TestResourceFactory(onClose: () => Unit = () => ()) {
    val created: ListBuffer[TestResource] = ListBuffer.empty
    def newResource(): TestResource = {
      val resource = new TestResource(onClose)
      created += resource
      resource
    }
  }

  "JdbcSessionUtil.tryWithResource" must {

    "create the resource only once and close the very same instance" in {
      val factory = new TestResourceFactory
      var passedToFunc: TestResource = null

      val result =
        JdbcSessionUtil.tryWithResource(factory.newResource()) { resource =>
          passedToFunc = resource
          resource.closeCount shouldBe 0
          "result"
        }

      result shouldBe "result"
      factory.created.toList should have size 1
      passedToFunc should be theSameInstanceAs factory.created.head
      passedToFunc.closeCount shouldBe 1
    }

    "close the resource passed to the function when the function throws" in {
      val factory = new TestResourceFactory

      val ex = intercept[RuntimeException] {
        JdbcSessionUtil.tryWithResource(factory.newResource()) { _ =>
          throw new RuntimeException("boom")
        }
      }

      ex.getMessage shouldBe "boom"
      factory.created.toList should have size 1
      factory.created.head.closeCount shouldBe 1
    }

    "ignore a SQLException thrown when closing the resource" in {
      val factory = new TestResourceFactory(() => throw new SQLException("can't close"))

      JdbcSessionUtil.tryWithResource(factory.newResource())(_ => "result") shouldBe "result"

      factory.created.toList should have size 1
      factory.created.head.closeCount shouldBe 1
    }
  }
}
