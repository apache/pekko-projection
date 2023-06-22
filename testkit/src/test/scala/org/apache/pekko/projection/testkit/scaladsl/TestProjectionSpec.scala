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

package org.apache.pekko.projection.testkit.scaladsl

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.projection.ProjectionId
import pekko.projection.scaladsl.ActorHandler
import pekko.stream.scaladsl.Source
import pekko.util.Timeout
import org.scalatest.wordspec.AnyWordSpecLike

object TestProjectionSpec {
  trait Command
  final case class Handle(envelope: Int, replyTo: ActorRef[Done]) extends Command

  def appender(strBuffer: StringBuffer): Behavior[Command] = Behaviors.receiveMessage {
    case Handle(i, replyTo) =>
      if (strBuffer.toString.isEmpty) strBuffer.append(i)
      else strBuffer.append("-").append(i)
      replyTo.tell(Done)
      appender(strBuffer)
  }

  class AppenderActorHandler(behavior: Behavior[Command])(implicit system: ActorSystem[_])
      extends ActorHandler[Int, Command](behavior) {
    import pekko.actor.typed.scaladsl.AskPattern._

    implicit val askTimeout: Timeout = 5.seconds

    override def process(actor: ActorRef[Command], envelope: Int): Future[Done] = {
      actor.ask[Done](replyTo => Handle(envelope, replyTo))
    }
  }
}

class TestProjectionSpec extends ScalaTestWithActorTestKit with LogCapturing with AnyWordSpecLike {
  import TestProjectionSpec._

  val projectionTestKit: ProjectionTestKit = ProjectionTestKit(system)
  val projectionId: ProjectionId = ProjectionId("name", "key")

  "TestProjection" must {

    "run an ActorHandler" in {
      val strBuffer = new StringBuffer()
      val sp = TestSourceProvider(Source(1 to 6), (i: Int) => i)
      val prj = TestProjection(projectionId, sp, () => new AppenderActorHandler(appender(strBuffer)))

      // stop as soon we observe that all expected elements passed through
      projectionTestKit.run(prj) {
        strBuffer.toString shouldBe "1-2-3-4-5-6"
      }
    }
  }
}
