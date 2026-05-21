/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.internal

import scala.collection.immutable
import scala.jdk.FutureConverters._
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.projection.r2dbc.javadsl
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession
import org.apache.pekko.projection.r2dbc.scaladsl
import scala.jdk.CollectionConverters._

/**
 * INTERNAL API: Adapter from javadsl.R2dbcHandler to scaladsl.R2dbcHandler
 */
@InternalApi private[projection] class R2dbcHandlerAdapter[Envelope](delegate: javadsl.R2dbcHandler[Envelope])
    extends scaladsl.R2dbcHandler[Envelope] {

  override def process(session: scaladsl.R2dbcSession, envelope: Envelope): Future[Done] = {
    delegate.process(new R2dbcSession(session.connection)(session.ec, session.system), envelope).toScala
  }

  override def start(): Future[Done] =
    delegate.start().toScala

  override def stop(): Future[Done] =
    delegate.stop().toScala

}

/**
 * INTERNAL API: Adapter from `javadsl.R2dbcHandler[java.util.List[Envelope]]` to
 * `scaladsl.R2dbcHandler[immutable.Seq[Envelope]]`
 */
@InternalApi private[projection] class R2dbcGroupedHandlerAdapter[Envelope](
    delegate: javadsl.R2dbcHandler[java.util.List[Envelope]])
    extends scaladsl.R2dbcHandler[immutable.Seq[Envelope]] {

  override def process(session: scaladsl.R2dbcSession, envelopes: immutable.Seq[Envelope]): Future[Done] = {
    delegate.process(new R2dbcSession(session.connection)(session.ec, session.system), envelopes.asJava).toScala
  }

  override def start(): Future[Done] =
    delegate.start().toScala

  override def stop(): Future[Done] =
    delegate.stop().toScala

}
