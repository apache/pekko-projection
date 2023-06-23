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

package org.apache.pekko.projection.jdbc.scaladsl

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.projection.jdbc.JdbcHandlerLifecycle
import pekko.projection.jdbc.JdbcSession

/**
 * Implement this interface for the Envelope handler for  Jdbc Projections.
 *
 * It can be stateful, with variables and mutable data structures.
 * It is invoked by the `Projection` machinery one envelope at a time and visibility
 * guarantees between the invocations are handled automatically, i.e. no volatile or
 * other concurrency primitives are needed for managing the state.
 *
 * Supported error handling strategies for when processing an `Envelope` fails can be
 * defined in configuration or using the `withRecoveryStrategy` method of a `Projection`
 * implementation.
 */
@ApiMayChange
trait JdbcHandler[Envelope, S <: JdbcSession] extends JdbcHandlerLifecycle {

  /**
   * The `process` method is invoked for each `Envelope`. Each time a new [[JdbcSession]] is passed with a new open transaction.
   * It's allowed to run any blocking JDBC operation inside this method.
   *
   * One envelope is processed at a time. It will not be invoked with the next envelope until after this method returns.
   */
  def process(session: S, envelope: Envelope): Unit

}

@ApiMayChange
object JdbcHandler {

  /** JdbcHandler that can be define from a simple function */
  private class JdbcHandlerFunction[Envelope, S <: JdbcSession](handler: (S, Envelope) => Unit)
      extends JdbcHandler[Envelope, S] {

    override def process(session: S, envelope: Envelope): Unit = handler(session, envelope)
  }

  def apply[S <: JdbcSession, Envelope](handler: (S, Envelope) => Unit): JdbcHandler[Envelope, S] =
    new JdbcHandlerFunction(handler)
}
