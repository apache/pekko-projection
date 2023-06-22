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

package org.apache.pekko.projection.eventsourced

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.persistence.query.Offset
import pekko.util.HashCode

@ApiMayChange
object EventEnvelope {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[projection] def apply[Event](eventEnvelope: pekko.persistence.query.EventEnvelope): EventEnvelope[Event] = {
    new EventEnvelope(
      offset = eventEnvelope.offset,
      persistenceId = eventEnvelope.persistenceId,
      sequenceNr = eventEnvelope.sequenceNr,
      event = eventEnvelope.event.asInstanceOf[Event],
      timestamp = eventEnvelope.timestamp)
  }

  def apply[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long): EventEnvelope[Event] =
    new EventEnvelope(offset, persistenceId, sequenceNr, event, timestamp)

  def create[Event](
      offset: Offset,
      persistenceId: String,
      sequenceNr: Long,
      event: Event,
      timestamp: Long): EventEnvelope[Event] = apply(offset, persistenceId, sequenceNr, event, timestamp)

  def unapply[Event](arg: EventEnvelope[Event]): Option[(Offset, String, Long, Event, Long)] =
    Some((arg.offset, arg.persistenceId, arg.sequenceNr, arg.event, arg.timestamp))
}

@ApiMayChange
final class EventEnvelope[Event](
    val offset: Offset,
    val persistenceId: String,
    val sequenceNr: Long,
    val event: Event,
    val timestamp: Long) {

  override def hashCode(): Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, offset)
    result = HashCode.hash(result, persistenceId)
    result = HashCode.hash(result, sequenceNr)
    result = HashCode.hash(result, event)
    result = HashCode.hash(result, timestamp)
    result
  }

  override def equals(other: Any): Boolean =
    other match {
      case that: EventEnvelope[_] =>
        offset == that.offset &&
        persistenceId == that.persistenceId &&
        sequenceNr == that.sequenceNr &&
        event == that.event &&
        timestamp == that.timestamp

      case _ => false
    }
}
