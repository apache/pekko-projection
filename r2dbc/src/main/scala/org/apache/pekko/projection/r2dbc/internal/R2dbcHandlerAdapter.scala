/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.internal

import scala.collection.immutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.projection.r2dbc.javadsl
import pekko.projection.r2dbc.javadsl.R2dbcSession
import pekko.projection.r2dbc.scaladsl

/**
 * INTERNAL API: Adapter from javadsl.R2dbcHandler to scaladsl.R2dbcHandler
 */
@InternalApi private[projection] class R2dbcHandlerAdapter[Envelope](delegate: javadsl.R2dbcHandler[Envelope])
    extends scaladsl.R2dbcHandler[Envelope] {

  override def process(session: scaladsl.R2dbcSession, envelope: Envelope): Future[Done] = {
    delegate.process(new R2dbcSession(session.connection)(session.ec, session.system), envelope).asScala
  }

  override def start(): Future[Done] =
    delegate.start().asScala

  override def stop(): Future[Done] =
    delegate.stop().asScala

}

/**
 * INTERNAL API: Adapter from `javadsl.R2dbcHandler[java.util.List[Envelope]]` to
 * `scaladsl.R2dbcHandler[immutable.Seq[Envelope]]`
 */
@InternalApi private[projection] class R2dbcGroupedHandlerAdapter[Envelope](
    delegate: javadsl.R2dbcHandler[java.util.List[Envelope]])
    extends scaladsl.R2dbcHandler[immutable.Seq[Envelope]] {

  override def process(session: scaladsl.R2dbcSession, envelopes: immutable.Seq[Envelope]): Future[Done] = {
    delegate.process(new R2dbcSession(session.connection)(session.ec, session.system), envelopes.asJava).asScala
  }

  override def start(): Future[Done] =
    delegate.start().asScala

  override def stop(): Future[Done] =
    delegate.stop().asScala

}
