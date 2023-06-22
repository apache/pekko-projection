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

package org.apache.pekko.projection.testkit.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.DoNotInherit
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState

@DoNotInherit
trait TestOffsetStore[Offset] {

  /**
   * The last saved offset to the offset store.
   */
  def lastOffset(): Option[Offset]

  /**
   * All offsets saved to the offset store.
   */
  def allOffsets(): List[(ProjectionId, Offset)]

  def readOffsets(): Future[Option[Offset]]

  def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done]

  def readManagementState(projectionId: ProjectionId): Future[Option[ManagementState]]

  def savePaused(projectionId: ProjectionId, paused: Boolean): Future[Done]
}
