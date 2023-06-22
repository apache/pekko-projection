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

import scala.jdk.CollectionConverters._
import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.testkit.scaladsl.TestOffsetStore
import pekko.util.OptionConverters._
import pekko.util.FutureConverters._

@InternalApi private[projection] class TestOffsetStoreAdapter[Offset](
    delegate: pekko.projection.testkit.javadsl.TestOffsetStore[Offset])
    extends TestOffsetStore[Offset] {

  override def lastOffset(): Option[Offset] = delegate.lastOffset().toScala

  override def allOffsets(): List[(ProjectionId, Offset)] = delegate.allOffsets().asScala.map(_.toScala).toList

  override def readOffsets(): Future[Option[Offset]] = {
    implicit val ec = pekko.dispatch.ExecutionContexts.parasitic
    delegate.readOffsets().asScala.map(_.toScala)
  }

  override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] =
    delegate.saveOffset(projectionId, offset).asScala

  override def readManagementState(projectionId: ProjectionId): Future[Option[ManagementState]] = {
    implicit val ec = pekko.dispatch.ExecutionContexts.parasitic
    delegate.readManagementState(projectionId).asScala.map(_.toScala)
  }

  override def savePaused(projectionId: ProjectionId, paused: Boolean): Future[Done] =
    delegate.savePaused(projectionId, paused).asScala
}
