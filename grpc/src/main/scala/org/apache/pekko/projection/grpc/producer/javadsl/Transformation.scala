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

package org.apache.pekko.projection.grpc.producer.javadsl

import org.apache.pekko
import pekko.annotation.ApiMayChange

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }
import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag
import pekko.projection.grpc.producer.scaladsl

@ApiMayChange
object Transformation {
  val empty: Transformation = new Transformation(scaladsl.EventProducer.Transformation.empty)

  /**
   * No transformation. Pass through each event as is.
   */
  val identity: Transformation = new Transformation(scaladsl.EventProducer.Transformation.identity)
}

/**
 * Transformation of events to the external (public) representation.
 * Events can be excluded by mapping them to `Optional.empty`.
 */
@ApiMayChange
final class Transformation private (private[grpc] val delegate: scaladsl.EventProducer.Transformation) {

  def registerAsyncMapper[A, B](
      inputEventClass: Class[A],
      f: JFunction[A, CompletionStage[Optional[B]]]): Transformation = {
    implicit val ct: ClassTag[A] = ClassTag(inputEventClass)
    new Transformation(
      delegate.registerAsyncMapper[A, B](event => f.apply(event).asScala.map(_.toScala)(ExecutionContext.parasitic)))
  }

  def registerMapper[A, B](inputEventClass: Class[A], f: JFunction[A, Optional[B]]): Transformation = {
    implicit val ct: ClassTag[A] = ClassTag(inputEventClass)
    new Transformation(delegate.registerMapper[A, B](event => f.apply(event).toScala))
  }

  def registerAsyncOrElseMapper(f: AnyRef => CompletionStage[Optional[AnyRef]]): Transformation = {
    new Transformation(
      delegate.registerAsyncOrElseMapper(event =>
        f.apply(event.asInstanceOf[AnyRef])
          .asScala
          .map(_.toScala)(ExecutionContext.parasitic)))
  }

  def registerOrElseMapper(f: AnyRef => Optional[AnyRef]): Transformation = {
    new Transformation(delegate.registerOrElseMapper(event => f.apply(event.asInstanceOf[AnyRef]).toScala))
  }
}
