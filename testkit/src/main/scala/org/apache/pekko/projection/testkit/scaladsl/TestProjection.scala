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

package org.apache.pekko.projection.testkit.scaladsl

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.projection.Projection
import pekko.projection.ProjectionId
import pekko.projection.StatusObserver
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.NoopStatusObserver
import pekko.projection.internal.OffsetStrategy
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.scaladsl.Handler
import pekko.projection.scaladsl.SourceProvider
import pekko.projection.testkit.internal.TestInMemoryOffsetStoreImpl
import pekko.projection.testkit.internal.TestProjectionImpl

@ApiMayChange
object TestProjection {

  /**
   * Create a [[TestProjection]] that can be used to assert a [[pekko.projection.scaladsl.Handler]] implementation.
   *
   * The [[TestProjection]] allows the user to test their [[pekko.projection.scaladsl.Handler]] implementation in
   * isolation, without requiring the Projection implementation (i.e. a database) to exist at test runtime.
   *
   * The [[pekko.projection.scaladsl.SourceProvider]] can be a concrete implementation, or a [[TestSourceProvider]] to
   * provide further test isolation.
   *
   * The [[TestProjection]] uses an at-least-once offset saving strategy where an offset is saved for each element.
   *
   * The [[TestProjection]] does not support grouping, at least once offset batching, or restart backoff strategies.
   *
   * @param projectionId - a Projection ID
   * @param sourceProvider - a [[pekko.projection.scaladsl.SourceProvider]] to supply envelopes to the Projection
   * @param handler - a user-defined [[pekko.projection.scaladsl.Handler]] to run within the Projection
   */
  def apply[Offset, Envelope](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[Envelope]): TestProjection[Offset, Envelope] =
    new TestProjectionImpl(
      projectionId = projectionId,
      sourceProvider = sourceProvider,
      handlerStrategy = SingleHandlerStrategy(handler),
      // Disable batching so that `ProjectionTestKit.runWithTestSink` emits 1 `Done` per envelope.
      offsetStrategy = AtLeastOnce(afterEnvelopes = Some(1)),
      statusObserver = NoopStatusObserver,
      offsetStoreFactory = () => new TestInMemoryOffsetStoreImpl[Offset](),
      startOffset = None)
}

@ApiMayChange
trait TestProjection[Offset, Envelope] extends Projection[Envelope] {
  def withStatusObserver(observer: StatusObserver[Envelope]): TestProjection[Offset, Envelope]

  /**
   * The initial offset of the offset store.
   */
  def withStartOffset(offset: Offset): TestProjection[Offset, Envelope]

  /**
   * The offset store factory. The offset store has the same lifetime as the Projection. It is instantiated when the
   * projection is first run and is created with [[newState]].
   */
  def withOffsetStoreFactory(factory: () => TestOffsetStore[Offset]): TestProjection[Offset, Envelope]

  /**
   * INTERNAL API: Choose a different [[OffsetStrategy]] for saving offsets. This is intended for Projection development only.
   */
  @InternalApi
  private[projection] def withOffsetStrategy(strategy: OffsetStrategy): TestProjection[Offset, Envelope]
}
