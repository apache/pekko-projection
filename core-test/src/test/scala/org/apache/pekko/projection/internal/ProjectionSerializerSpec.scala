/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.internal

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.projection.ProjectionBehavior
import pekko.projection.ProjectionId
import pekko.serialization.SerializationExtension
import org.scalatest.wordspec.AnyWordSpecLike

class ProjectionSerializerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  import ProjectionBehavior.Internal._

  private val classicSystem = system.toClassic
  private val serializer = new ProjectionSerializer(classicSystem.asInstanceOf[ExtendedActorSystem])
  private val ref = spawn(Behaviors.empty[Any])
  private val projectionId = ProjectionId("test", "3")

  "ProjectionSerializer" must {

    Seq(
      "GetOffset" -> GetOffset(projectionId, ref),
      "CurrentOffset Some" -> CurrentOffset(projectionId, Some(17)),
      "CurrentOffset None" -> CurrentOffset(projectionId, None),
      "SetOffset Some" -> SetOffset(projectionId, Some(17), ref),
      "SetOffset None" -> SetOffset(projectionId, None, ref),
      "IsPaused" -> IsPaused(projectionId, ref),
      "SetPaused true" -> SetPaused(projectionId, paused = true, ref),
      "SetPaused false" -> SetPaused(projectionId, paused = false, ref)).foreach {
      case (scenario, item) =>
        s"resolve serializer for $scenario" in {
          val serializer = SerializationExtension(classicSystem)
          serializer.serializerFor(item.getClass).getClass should be(classOf[ProjectionSerializer])
        }

        s"serialize and de-serialize $scenario" in {
          verifySerialization(item)
        }
    }
  }

  def verifySerialization(msg: AnyRef): Unit = {
    serializer.fromBinary(serializer.toBinary(msg), serializer.manifest(msg)) should be(msg)
  }

}
