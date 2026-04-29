/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.internal

import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.projection.ProjectionId

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object TestData {
  private val start =
    0L // could be something more unique, like currentTimeMillis
  private val pidCounter = new AtomicLong(start)
  private val entityTypeCounter = new AtomicLong(start)
}

trait TestData {
  import TestData.entityTypeCounter
  import TestData.pidCounter

  def nextPid(entityType: String): PersistenceId =
    PersistenceId(entityType, s"p-${pidCounter.incrementAndGet()}")

  def nextEntityType() = s"TestEntity-${entityTypeCounter.incrementAndGet()}"

  def randomProjectionId(): ProjectionId =
    ProjectionId(UUID.randomUUID().toString, "00")

}
