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

package org.apache.pekko.projection.internal

import scala.collection.immutable
import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.projection.javadsl
import pekko.projection.scaladsl
import pekko.util.ccompat.JavaConverters._
import pekko.util.FutureConverters._

/**
 * INTERNAL API
 */
@InternalApi private[projection] object HandlerAdapter {
  def apply[Envelope](delegate: javadsl.Handler[Envelope]): scaladsl.Handler[Envelope] = {
    delegate match {
      case a: javadsl.ActorHandler[Envelope, Any] @unchecked => new ActorHandlerAdapter[Envelope](a)
      case _                                                 => new HandlerAdapter(delegate)
    }
  }
}

/**
 * INTERNAL API: Adapter from javadsl.Handler to scaladsl.Handler
 */
@InternalApi private[projection] class HandlerAdapter[Envelope](delegate: javadsl.Handler[Envelope])
    extends scaladsl.Handler[Envelope] {

  override def process(envelope: Envelope): Future[Done] = {
    delegate.process(envelope).asScala
  }

  override def start(): Future[Done] =
    delegate.start().asScala

  override def stop(): Future[Done] =
    delegate.stop().asScala

}

/**
 * INTERNAL API: Adapter from `javadsl.Handler[java.util.List[Envelope]]` to `scaladsl.Handler[immutable.Seq[Envelope]]`
 */
@InternalApi private[projection] class GroupedHandlerAdapter[Envelope](
    delegate: javadsl.Handler[java.util.List[Envelope]])
    extends scaladsl.Handler[immutable.Seq[Envelope]] {

  override def process(envelopes: immutable.Seq[Envelope]): Future[Done] = {
    delegate.process(envelopes.asJava).asScala
  }

  override def start(): Future[Done] =
    delegate.start().asScala

  override def stop(): Future[Done] =
    delegate.stop().asScala

}

/**
 * INTERNAL API: Adapter from javadsl.HandlerLifecycle to scaladsl.HandlerLifecycle
 */
@InternalApi
private[projection] class HandlerLifecycleAdapter(delegate: javadsl.HandlerLifecycle)
    extends scaladsl.HandlerLifecycle {

  /**
   * Invoked when the projection is starting, before first envelope is processed.
   * Can be overridden to implement initialization. It is also called when the `Projection`
   * is restarted after a failure.
   */
  override def start(): Future[Done] =
    delegate.start().asScala

  /**
   * Invoked when the projection has been stopped. Can be overridden to implement resource
   * cleanup. It is also called when the `Projection` is restarted after a failure.
   */
  override def stop(): Future[Done] =
    delegate.stop().asScala
}

/**
 * INTERNAL API: Adapter from javadsl.ActorHandler to scaladsl.ActorHandler
 */
@InternalApi private[projection] class ActorHandlerAdapter[Envelope](delegate: javadsl.ActorHandler[Envelope, Any])
    extends scaladsl.Handler[Envelope]
    with ActorHandlerInit[Any] {

  override private[projection] def behavior = delegate.behavior

  override final def process(envelope: Envelope): Future[Done] =
    delegate.process(getActor(), envelope).asScala

  override def start(): Future[Done] =
    delegate.start().asScala

  override def stop(): Future[Done] =
    delegate.stop().asScala

}
