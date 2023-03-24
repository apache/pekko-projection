/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.testkit.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

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
  def lastOffset(): Optional[Offset]

  /**
   * All offsets saved to the offset store.
   */
  def allOffsets(): java.util.List[pekko.japi.Pair[ProjectionId, Offset]]

  def readOffsets(): CompletionStage[Optional[Offset]]

  def saveOffset(projectionId: ProjectionId, offset: Offset): CompletionStage[Done]

  def readManagementState(projectionId: ProjectionId): CompletionStage[Optional[ManagementState]]

  def savePaused(projectionId: ProjectionId, paused: Boolean): CompletionStage[Done]
}
