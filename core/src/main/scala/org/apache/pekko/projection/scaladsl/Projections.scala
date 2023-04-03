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

package org.apache.pekko.projection.scaladsl

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.projection.HandlerRecoveryStrategy
import pekko.projection.Projection
import pekko.projection.StatusObserver
import pekko.projection.StrictRecoveryStrategy
import pekko.projection.internal.InternalProjection
@DoNotInherit
trait ExactlyOnceProjection[Offset, Envelope] extends Projection[Envelope] {
  self: InternalProjection =>

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): ExactlyOnceProjection[Offset, Envelope]

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): ExactlyOnceProjection[Offset, Envelope]

  override def withStatusObserver(observer: StatusObserver[Envelope]): ExactlyOnceProjection[Offset, Envelope]

  def withRecoveryStrategy(recoveryStrategy: HandlerRecoveryStrategy): ExactlyOnceProjection[Offset, Envelope]
}

@DoNotInherit
trait AtLeastOnceFlowProjection[Offset, Envelope] extends Projection[Envelope] {
  self: InternalProjection =>

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): AtLeastOnceFlowProjection[Offset, Envelope]

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): AtLeastOnceFlowProjection[Offset, Envelope]

  override def withStatusObserver(observer: StatusObserver[Envelope]): AtLeastOnceFlowProjection[Offset, Envelope]

  def withSaveOffset(afterEnvelopes: Int, afterDuration: FiniteDuration): AtLeastOnceFlowProjection[Offset, Envelope]
}

@DoNotInherit
trait AtLeastOnceProjection[Offset, Envelope] extends Projection[Envelope] {
  self: InternalProjection =>

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): AtLeastOnceProjection[Offset, Envelope]

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): AtLeastOnceProjection[Offset, Envelope]

  override def withStatusObserver(observer: StatusObserver[Envelope]): AtLeastOnceProjection[Offset, Envelope]

  def withSaveOffset(afterEnvelopes: Int, afterDuration: FiniteDuration): AtLeastOnceProjection[Offset, Envelope]

  def withRecoveryStrategy(recoveryStrategy: HandlerRecoveryStrategy): AtLeastOnceProjection[Offset, Envelope]
}

@DoNotInherit
trait AtMostOnceProjection[Offset, Envelope] extends Projection[Envelope] {
  self: InternalProjection =>

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): AtMostOnceProjection[Offset, Envelope]

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): AtMostOnceProjection[Offset, Envelope]

  override def withStatusObserver(observer: StatusObserver[Envelope]): AtMostOnceProjection[Offset, Envelope]

  def withRecoveryStrategy(recoveryStrategy: StrictRecoveryStrategy): AtMostOnceProjection[Offset, Envelope]
}

@DoNotInherit
trait GroupedProjection[Offset, Envelope] extends Projection[Envelope] {
  self: InternalProjection =>

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): GroupedProjection[Offset, Envelope]

  override def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): GroupedProjection[Offset, Envelope]

  override def withStatusObserver(observer: StatusObserver[Envelope]): GroupedProjection[Offset, Envelope]

  def withGroup(groupAfterEnvelopes: Int, groupAfterDuration: FiniteDuration): GroupedProjection[Offset, Envelope]

  def withRecoveryStrategy(recoveryStrategy: HandlerRecoveryStrategy): GroupedProjection[Offset, Envelope]
}
