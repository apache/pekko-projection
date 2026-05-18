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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

import org.apache.pekko.annotation.InternalApi

object TestClock {
  def nowMillis(): TestClock = new TestClock(ChronoUnit.MILLIS)
  def nowMicros(): TestClock = new TestClock(ChronoUnit.MICROS)
}

/**
 * Clock for testing purpose, which is truncated to a resolution (milliseconds or microseconds).
 *
 * The reason for truncating to the resolution is that Postgres timestamps have the resolution of microseconds but some
 * OS/JDK (Linux/JDK17) has Instant resolution of nanoseconds.
 */
@InternalApi private[projection] class TestClock(resolution: TemporalUnit) extends Clock {

  @volatile private var _instant = Instant.now().truncatedTo(resolution)

  override def getZone: ZoneId = ZoneOffset.UTC

  override def withZone(zone: ZoneId): Clock =
    throw new UnsupportedOperationException("withZone not supported")

  override def instant(): Instant =
    _instant

  def setInstant(newInstant: Instant): Unit =
    _instant = newInstant.truncatedTo(resolution)

  /**
   * Increase the clock with this duration (truncated to the resolution)
   */
  def tick(duration: Duration): Instant = {
    val newInstant = _instant.plus(duration).truncatedTo(resolution)
    _instant = newInstant
    newInstant
  }

}
