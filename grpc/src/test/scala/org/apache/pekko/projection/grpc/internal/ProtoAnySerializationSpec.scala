/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.internal

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.projection.grpc.internal.ProtoAnySerialization.PekkoSerializationTypeUrlPrefix
import pekko.projection.grpc.internal.proto.TestEvent
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ProtoAnySerializationSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.load())
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {

  private val serializer = new ProtoAnySerialization(system)

  "ProtoAnySerialization" must {
    "serialize proto message" in {
      val testEvent = TestEvent("test-message")
      val anyEvent = serializer.serialize(testEvent)
      anyEvent.typeUrl shouldBe "type.googleapis.com/org.apache.pekko.projection.grpc.internal.TestEvent"
    }

    "serialize and deserialize proto message with descriptor" in {
      val serializerWithDescriptors = new ProtoAnySerialization(
        system,
        List(TestEvent.javaDescriptor.getFile),
        ProtoAnySerialization.Prefer.Scala)
      val testEvent = TestEvent("test-message")
      val anyEvent = serializerWithDescriptors.serialize(testEvent)
      anyEvent.typeUrl shouldBe "type.googleapis.com/org.apache.pekko.projection.grpc.internal.TestEvent"
      val deserialized = serializerWithDescriptors.deserialize(anyEvent)
      deserialized shouldBe testEvent
    }

    "serialize pekko serializable message" in {
      val testEvent = WrappedEvent(42L, "pekko-event")
      val anyEvent = serializer.serialize(testEvent)
      anyEvent.typeUrl should startWith(PekkoSerializationTypeUrlPrefix)
    }

    "deserialize pekko serializable message" in {
      val testEvent = WrappedEvent(42L, "pekko-event")
      val anyEvent = serializer.serialize(testEvent)
      val deserialized = serializer.deserialize(anyEvent)
      deserialized shouldBe testEvent
    }

    "serialize string" in {
      val anyEvent = serializer.serialize("hello")
      anyEvent.typeUrl should startWith(PekkoSerializationTypeUrlPrefix)
    }

    "deserialize string" in {
      val anyEvent = serializer.serialize("hello")
      val deserialized = serializer.deserialize(anyEvent)
      deserialized shouldBe "hello"
    }
  }
}

final case class WrappedEvent(id: Long, msg: String)
