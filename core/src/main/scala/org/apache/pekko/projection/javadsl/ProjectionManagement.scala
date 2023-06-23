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

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.projection.ProjectionId
import pekko.projection.scaladsl
import pekko.util.FutureConverters._
import pekko.util.OptionConverters._

@ApiMayChange object ProjectionManagement {
  def get(system: ActorSystem[_]): ProjectionManagement = new ProjectionManagement(system)
}

@ApiMayChange class ProjectionManagement(system: ActorSystem[_]) {
  private val delegate = scaladsl.ProjectionManagement(system)
  private implicit val ec: ExecutionContext = system.executionContext

  /**
   * Get the latest stored offset for the `projectionId`.
   */
  def getOffset[Offset](projectionId: ProjectionId): CompletionStage[Optional[Offset]] =
    delegate.getOffset[Offset](projectionId).map(_.toJava).asJava

  /**
   * Update the stored offset for the `projectionId` and restart the `Projection`.
   * This can be useful if the projection was stuck with errors on a specific offset and should skip
   * that offset and continue with next. Note that when the projection is restarted it will continue from
   * the next offset that is greater than the stored offset.
   */
  def updateOffset[Offset](projectionId: ProjectionId, offset: Offset): CompletionStage[Done] =
    delegate.updateOffset[Offset](projectionId, offset).asJava

  /**
   * Clear the stored offset for the `projectionId` and restart the `Projection`.
   * This can be useful if the projection should be completely rebuilt, starting over again from the first
   * offset.
   */
  def clearOffset(projectionId: ProjectionId): CompletionStage[Done] =
    delegate.clearOffset(projectionId).asJava

  /**
   * Is the given Projection paused or not?
   */
  def isPaused(projectionId: ProjectionId): CompletionStage[java.lang.Boolean] =
    delegate.isPaused(projectionId).map(java.lang.Boolean.valueOf).asJava

  /**
   * Pause the given Projection. Processing will be stopped.
   * While the Projection is paused other management operations can be performed, such as
   * [[ProjectionManagement.resume]].
   * The Projection can be resumed with [[ProjectionManagement.resume]].
   *
   * The paused/resumed state is stored, and it is read when the Projections are started, for example
   * in case of rebalance or system restart.
   */
  def pause(projectionId: ProjectionId): CompletionStage[Done] =
    delegate.pause(projectionId).asJava

  /**
   * Resume a paused Projection. Processing will be start from previously stored offset.
   *
   * The paused/resumed state is stored, and it is read when the Projections are started, for example
   * in case of rebalance or system restart.
   */
  def resume(projectionId: ProjectionId): CompletionStage[Done] =
    delegate.resume(projectionId).asJava

}
