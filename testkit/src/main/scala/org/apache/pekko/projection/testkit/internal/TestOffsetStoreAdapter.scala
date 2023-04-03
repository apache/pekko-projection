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

package org.apache.pekko.projection.testkit.internal

import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.projection.ProjectionId
import pekko.projection.internal.ManagementState
import pekko.projection.testkit.scaladsl.TestOffsetStore
@InternalApi private[projection] class TestOffsetStoreAdapter[Offset](
    delegate: pekko.projection.testkit.javadsl.TestOffsetStore[Offset])
    extends TestOffsetStore[Offset] {

  override def lastOffset(): Option[Offset] = delegate.lastOffset().asScala

  override def allOffsets(): List[(ProjectionId, Offset)] = delegate.allOffsets().asScala.map(_.toScala).toList

  override def readOffsets(): Future[Option[Offset]] = {
    implicit val ec = pekko.dispatch.ExecutionContexts.parasitic
    delegate.readOffsets().toScala.map(_.asScala)
  }

  override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] =
    delegate.saveOffset(projectionId, offset).toScala

  override def readManagementState(projectionId: ProjectionId): Future[Option[ManagementState]] = {
    implicit val ec = pekko.dispatch.ExecutionContexts.parasitic
    delegate.readManagementState(projectionId).toScala.map(_.asScala)
  }

  override def savePaused(projectionId: ProjectionId, paused: Boolean): Future[Done] =
    delegate.savePaused(projectionId, paused).toScala
}
