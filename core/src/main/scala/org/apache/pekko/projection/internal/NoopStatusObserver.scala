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

package org.apache.pekko.projection.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.projection.HandlerRecoveryStrategy
import pekko.projection.ProjectionId
import pekko.projection.StatusObserver

/**
 * INTERNAL API
 */
@InternalApi private[projection] object NoopStatusObserver extends StatusObserver[Any] {

  // Java access
  def getInstance[Envelope]: StatusObserver[Envelope] = NoopStatusObserver

  override def started(projectionId: ProjectionId): Unit = ()

  override def failed(projectionId: ProjectionId, cause: Throwable): Unit = ()

  override def stopped(projectionId: ProjectionId): Unit = ()

  override def beforeProcess(projectionId: ProjectionId, envelope: Any): Unit = ()

  override def afterProcess(projectionId: ProjectionId, envelope: Any): Unit = ()

  override def offsetProgress(projectionId: ProjectionId, env: Any): Unit = ()

  override def error(
      projectionId: ProjectionId,
      env: Any,
      cause: Throwable,
      recoveryStrategy: HandlerRecoveryStrategy): Unit = ()
}
