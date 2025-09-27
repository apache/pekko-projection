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

package org.apache.pekko.projection.jdbc.internal

import scala.collection.immutable
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.projection.jdbc.JdbcSession
import pekko.projection.jdbc.javadsl
import pekko.projection.jdbc.scaladsl

/**
 * INTERNAL API: Adapter from javadsl.JdbcHandler to scaladsl.JdbcHandler
 */
@InternalApi private[projection] class JdbcHandlerAdapter[Envelope, S <: JdbcSession](
    delegate: javadsl.JdbcHandler[Envelope, S])
    extends scaladsl.JdbcHandler[Envelope, S] {

  override def process(session: S, envelope: Envelope): Unit = {
    delegate.process(session, envelope)
  }

  override def start(): Unit = delegate.start()
  override def stop(): Unit = delegate.stop()
}

/**
 * INTERNAL API: Adapter from `javadsl.Handler[java.util.List[Envelope]]` to `scaladsl.Handler[immutable.Seq[Envelope]]`
 */
@InternalApi private[projection] class GroupedJdbcHandlerAdapter[Envelope, S <: JdbcSession](
    delegate: javadsl.JdbcHandler[java.util.List[Envelope], S])
    extends scaladsl.JdbcHandler[immutable.Seq[Envelope], S] {

  override def process(session: S, envelopes: immutable.Seq[Envelope]): Unit = {
    delegate.process(session, envelopes.asJava)
  }

  override def start(): Unit = delegate.start()
  override def stop(): Unit = delegate.stop()
}
