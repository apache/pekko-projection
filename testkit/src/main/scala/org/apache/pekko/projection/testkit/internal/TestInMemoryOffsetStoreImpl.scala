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

package org.apache.pekko.projection.testkit.internal

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.testkit.scaladsl.TestOffsetStore

/**
 * INTERNAL API
 */
@InternalApi
private[projection] class TestInMemoryOffsetStoreImpl[Offset] private[projection] () extends TestOffsetStore[Offset] {
  private var savedOffsets = List[(ProjectionId, Offset)]()
  private var savedPaused = Map.empty[ProjectionId, Boolean]

  override def lastOffset(): Option[Offset] =
    this.synchronized(savedOffsets.headOption.map { case (_, offset) => offset })

  override def allOffsets(): List[(ProjectionId, Offset)] = this.synchronized(savedOffsets)

  override def readOffsets(): Future[Option[Offset]] = this.synchronized { Future.successful(lastOffset()) }

  override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] = this.synchronized {
    savedOffsets = (projectionId -> offset) +: savedOffsets
    Future.successful(Done)
  }

  override def readManagementState(projectionId: ProjectionId): Future[Option[ManagementState]] = this.synchronized {
    Future.successful(savedPaused.get(projectionId).map(ManagementState.apply))
  }

  override def savePaused(projectionId: ProjectionId, paused: Boolean): Future[Done] = this.synchronized {
    savedPaused = savedPaused.updated(projectionId, paused)
    Future.successful(Done)
  }
}
