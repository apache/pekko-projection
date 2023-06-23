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

package org.apache.pekko.projection.javadsl

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.Done
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi

@ApiMayChange
object Handler {

  /**
   * INTERNAL API
   *
   * Handler that can be defined from a simple function
   */
  @InternalApi
  private class HandlerFunction[Envelope](handler: Envelope => CompletionStage[Done]) extends Handler[Envelope] {
    override def process(envelope: Envelope): CompletionStage[Done] = handler(envelope)
  }

  def fromFunction[Envelope](handler: Envelope => CompletionStage[Done]): Handler[Envelope] =
    new HandlerFunction(handler)
}

/**
 * Implement this interface for the Envelope handler in the `Projection`. Some projections
 * may have more specific handler types.
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
abstract class Handler[Envelope] extends HandlerLifecycle {

  /**
   * The `process` method is invoked for each `Envelope`.
   * One envelope is processed at a time. The returned `CompletionStage` is to be completed when the processing
   * of the `envelope` has finished. It will not be invoked with the next envelope until after the returned
   * `CompletionStage` has been completed.
   */
  @throws(classOf[Exception])
  def process(envelope: Envelope): CompletionStage[Done]

  /**
   * Invoked when the projection is starting, before first envelope is processed.
   * Can be overridden to implement initialization. It is also called when the `Projection`
   * is restarted after a failure.
   */
  def start(): CompletionStage[Done] =
    CompletableFuture.completedFuture(Done)

  /**
   * Invoked when the projection has been stopped. Can be overridden to implement resource
   * cleanup. It is also called when the `Projection` is restarted after a failure.
   */
  def stop(): CompletionStage[Done] =
    CompletableFuture.completedFuture(Done)
}

@ApiMayChange trait HandlerLifecycle {

  /**
   * Invoked when the projection is starting, before first envelope is processed.
   * Can be overridden to implement initialization.
   */
  def start(): CompletionStage[Done]

  /**
   * Invoked when the projection has been stopped. Can be overridden to implement resource
   * cleanup.
   */
  def stop(): CompletionStage[Done]
}
