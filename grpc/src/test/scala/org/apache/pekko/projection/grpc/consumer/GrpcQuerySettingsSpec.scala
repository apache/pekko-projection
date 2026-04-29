/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.projection.grpc.consumer

import org.apache.pekko.grpc.scaladsl.StringEntry
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcQuerySettingsSpec extends AnyWordSpecLike with Matchers {
  "The GrpcQuerySettings" should {
    "parse from config" in {
      val config = ConfigFactory.parseString(""" 
        stream-id = "my-stream-id"
        additional-request-headers {
          "x-auth-header" = "secret"
        }
      """)

      val settings = GrpcQuerySettings(config)
      settings.streamId shouldBe "my-stream-id"
      settings.additionalRequestMetadata.map(_.asList) shouldBe Some(List("x-auth-header" -> StringEntry("secret")))
    }
  }

}
