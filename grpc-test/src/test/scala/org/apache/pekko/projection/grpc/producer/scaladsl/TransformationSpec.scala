/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.producer.scaladsl

import java.util.Optional

import org.apache.pekko
import pekko.persistence.query.typed.EventEnvelope
import pekko.projection.grpc.producer.scaladsl.EventProducer.Transformation
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ Future, Promise }

class TransformationSpec extends AnyWordSpec with Matchers with ScalaFutures {

  def envelope(payload: String, meta: Option[Any] = None) =
    new EventEnvelope[String](
      offset = null,
      persistenceId = "pid1",
      sequenceNr = 1,
      Some(payload),
      timestamp = System.currentTimeMillis(),
      entityType = "banana",
      slice = 5,
      eventMetadata = meta,
      filtered = false,
      source = "").asInstanceOf[EventEnvelope[Any]]

  "The gRPC event Transformation" should {

    "transform simple" in {
      val transformer = Transformation.empty.registerMapper((_: String) => Some("mapped"))
      transformer(envelope("whatever")).futureValue should ===(Some("mapped"))
    }

    "transform filter out simple" in {
      val transformer = Transformation.empty.registerMapper((_: String) => None)
      transformer(envelope("whatever")).futureValue should ===(None)
    }

    "transform simple async" in {
      val transformer = Transformation.empty.registerAsyncMapper((_: String) => Future.successful(Some("mapped")))
      transformer(envelope("whatever")).futureValue should ===(Some("mapped"))
    }

    "transform low level with metadata" in {
      val transformer =
        Transformation.empty.registerAsyncEnvelopeMapper((env: EventEnvelope[String]) =>
          Future.successful(env.eventMetadata))
      transformer(envelope("whatever", Some("meta"))).futureValue should ===(Some("meta"))
    }

    "map or omit an event synchronously using its envelope metadata" in {
      val transformer = Transformation.empty.registerEnvelopeMapper((env: EventEnvelope[String]) => env.eventMetadata)
      transformer(envelope("payload", Some("metadata"))).futureValue should ===(Some("metadata"))
      transformer(envelope("payload")).futureValue should ===(None)
    }

    "keep exact event-class dispatch and use the last mapper registered for that class" in {
      val asyncResult = Promise[Option[String]]()
      val typed = Transformation.empty
        .registerOrElseMapper(_ => Some("fallback"))
        .registerEnvelopeMapper[Long, String](_ => Some("long"))
        .registerMapper[String, String](_ => Some("event"))
      val sync = typed.registerEnvelopeMapper[String, String](env => Some(env.persistenceId))
      sync(envelope("payload")).futureValue should ===(Some("pid1"))

      val async = sync.registerAsyncEnvelopeMapper[String, String](_ => asyncResult.future)
      val mapped = async(envelope("payload"))
      mapped.isCompleted shouldBe false
      asyncResult.success(Some("async"))
      mapped.futureValue should ===(Some("async"))
      sync(envelope("payload")).futureValue should ===(Some("pid1"))
      Transformation.empty
        .registerOrElseMapper(_ => Some("fallback"))
        .registerEnvelopeMapper[Long, String](_ => Some("long"))
        .apply(envelope("payload"))
        .futureValue should ===(Some("fallback"))
    }

    "preserve synchronous callback failures" in {
      val cause = new IllegalStateException("mapper failed")
      val transformer = Transformation.empty.registerEnvelopeMapper[String, String](_ => throw cause)
      (intercept[IllegalStateException](transformer(envelope("payload"))) eq cause) shouldBe true
    }

    "adapt a Java envelope mapper result and empty Optional" in {
      val transformer = Transformation.empty.toJava.registerEnvelopeMapper[String, String](
        classOf[String],
        env => if (env.event == "skip") Optional.empty[String]() else Optional.of(env.persistenceId))
      transformer.delegate(envelope("payload")).futureValue should ===(Some("pid1"))
      transformer.delegate(envelope("skip")).futureValue should ===(None)
    }

    "fail by default if no transformer exist for event" in {
      val transformer = Transformation.empty
      transformer(envelope("whatever", Some("meta"))).failed.futureValue
    }

    "fallback if no transformer exist for event" in {
      val transformer = Transformation.empty.registerOrElseMapper(_ => Some("fallback"))
      transformer(envelope("whatever", Some("meta"))).futureValue should ===(Some("fallback"))
    }

    "fallback low level with metadata if no transformer exist for event" in {
      val transformer = Transformation.empty.registerAsyncEnvelopeOrElseMapper((env: EventEnvelope[Any]) =>
        Future.successful(env.eventMetadata))
      transformer(envelope("whatever", Some("meta"))).futureValue should ===(Some("meta"))
    }
  }

}
