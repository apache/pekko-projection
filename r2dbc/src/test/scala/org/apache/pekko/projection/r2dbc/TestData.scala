/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import org.apache.pekko.projection.ProjectionId

object TestData {
  private val start = 0L // could be something more unique, like currentTimeMillis
  private val pidCounter = new AtomicLong(start)
  private val entityTypeCounter = new AtomicLong(start)
}

trait TestData {
  import TestData.pidCounter
  import TestData.entityTypeCounter

  def nextPid() = s"p-${pidCounter.incrementAndGet()}"
  // FIXME return PersistenceId instead
  def nextPid(entityType: String) = s"$entityType|p-${pidCounter.incrementAndGet()}"

  def nextEntityType() = s"TestEntity-${entityTypeCounter.incrementAndGet()}"

  def genRandomProjectionId(): ProjectionId = ProjectionId(UUID.randomUUID().toString, "00")

}
