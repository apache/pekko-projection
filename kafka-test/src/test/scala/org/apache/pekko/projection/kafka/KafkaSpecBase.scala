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

package org.apache.pekko.projection.kafka

import org.apache.pekko
import pekko.actor.{ typed, ActorSystem }
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.scaladsl.adapter._
import pekko.kafka.testkit.KafkaTestkitTestcontainersSettings
import pekko.kafka.testkit.internal.TestFrameworkInterface
import pekko.kafka.testkit.scaladsl.KafkaSpec
import pekko.kafka.testkit.scaladsl.TestcontainersKafkaPerClassLike
import pekko.projection.testkit.scaladsl.ProjectionTestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

abstract class KafkaSpecBase(val config: Config, kafkaPort: Int)
    extends KafkaSpec(kafkaPort, kafkaPort + 1, ActorSystem("Spec", config))
    with Suite
    with TestFrameworkInterface.Scalatest
    with AnyWordSpecLike
    with OptionValues
    with LogCapturing
    with ScalaFutures
    with Matchers
    with PatienceConfiguration
    with Eventually
    with BeforeAndAfterEach
    with TestcontainersKafkaPerClassLike {

  protected def this() = this(config = ConfigFactory.load, kafkaPort = -1)
  protected def this(config: Config) = this(config = config, kafkaPort = -1)

  val testKit = ActorTestKit(system.toTyped)
  val projectionTestKit = ProjectionTestKit(system.toTyped)

  implicit val actorSystem: typed.ActorSystem[Nothing] = testKit.system
  implicit val dispatcher: ExecutionContext = testKit.system.executionContext

  override val testcontainersSettings = KafkaTestkitTestcontainersSettings(system)
}
