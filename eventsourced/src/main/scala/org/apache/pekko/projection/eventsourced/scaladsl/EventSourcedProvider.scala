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

package org.apache.pekko.projection.eventsourced.scaladsl

import java.time.Instant

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.typesafe.config.Config
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.scaladsl.EventsByTagQuery
import pekko.persistence.query.typed.scaladsl.EventTimestampQuery
import pekko.persistence.query.typed.scaladsl.EventsBySliceQuery
import pekko.persistence.query.typed.scaladsl.LoadEventQuery
import pekko.projection.BySlicesSourceProvider
import pekko.projection.eventsourced.EventEnvelope
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.scaladsl.Source

@ApiMayChange
object EventSourcedProvider {

  def eventsByTag[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {

    val eventsByTagQuery =
      PersistenceQuery(system).readJournalFor[EventsByTagQuery](readJournalPluginId)

    new EventsByTagSourceProvider(eventsByTagQuery, tag, system)
  }

  def eventsByTag[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {

    val eventsByTagQuery =
      PersistenceQuery(system).readJournalFor[EventsByTagQuery](readJournalPluginId, readJournalConfig)

    new EventsByTagSourceProvider(eventsByTagQuery, tag, system)
  }

  private class EventsByTagSourceProvider[Event](
      eventsByTagQuery: EventsByTagQuery,
      tag: String,
      system: ActorSystem[_])
      extends SourceProvider[Offset, EventEnvelope[Event]] {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offset: () => Future[Option[Offset]]): Future[Source[EventEnvelope[Event], NotUsed]] =
      offset().map { offsetOpt =>
        val offset = offsetOpt.getOrElse(NoOffset)
        eventsByTagQuery
          .eventsByTag(tag, offset)
          .map(env => EventEnvelope(env))
      }

    override def extractOffset(envelope: EventEnvelope[Event]): Offset = envelope.offset

    override def extractCreationTime(envelope: EventEnvelope[Event]): Long = envelope.timestamp
  }

  def eventsBySlices[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).readJournalFor[EventsBySliceQuery](readJournalPluginId)

    new EventsBySlicesSourceProvider(eventsBySlicesQuery, entityType, minSlice, maxSlice, system)
  }

  def eventsBySlices[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).readJournalFor[EventsBySliceQuery](readJournalPluginId, readJournalConfig)

    new EventsBySlicesSourceProvider(eventsBySlicesQuery, entityType, minSlice, maxSlice, system)
  }

  def sliceForPersistenceId(system: ActorSystem[_], readJournalPluginId: String, persistenceId: String): Int =
    PersistenceQuery(system)
      .readJournalFor[EventsBySliceQuery](readJournalPluginId)
      .sliceForPersistenceId(persistenceId)

  def sliceForPersistenceId(
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      persistenceId: String): Int =
    PersistenceQuery(system)
      .readJournalFor[EventsBySliceQuery](readJournalPluginId, readJournalConfig)
      .sliceForPersistenceId(persistenceId)

  def sliceRanges(system: ActorSystem[_], readJournalPluginId: String, numberOfRanges: Int): immutable.Seq[Range] =
    PersistenceQuery(system).readJournalFor[EventsBySliceQuery](readJournalPluginId).sliceRanges(numberOfRanges)

  def sliceRanges(
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      numberOfRanges: Int): immutable.Seq[Range] =
    PersistenceQuery(system)
      .readJournalFor[EventsBySliceQuery](readJournalPluginId, readJournalConfig)
      .sliceRanges(numberOfRanges)

  private class EventsBySlicesSourceProvider[Event](
      eventsBySlicesQuery: EventsBySliceQuery,
      entityType: String,
      override val minSlice: Int,
      override val maxSlice: Int,
      system: ActorSystem[_])
      extends SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]]
      with BySlicesSourceProvider
      with EventTimestampQuery
      with LoadEventQuery {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offset: () => Future[Option[Offset]])
        : Future[Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed]] =
      offset().map { offsetOpt =>
        val offset = offsetOpt.getOrElse(NoOffset)
        eventsBySlicesQuery.eventsBySlices(entityType, minSlice, maxSlice, offset)
      }

    override def extractOffset(envelope: pekko.persistence.query.typed.EventEnvelope[Event]): Offset = envelope.offset

    override def extractCreationTime(envelope: pekko.persistence.query.typed.EventEnvelope[Event]): Long =
      envelope.timestamp

    override def timestampOf(persistenceId: String, sequenceNr: Long): Future[Option[Instant]] =
      eventsBySlicesQuery match {
        case timestampQuery: EventTimestampQuery =>
          timestampQuery.timestampOf(persistenceId, sequenceNr)
        case _ =>
          Future.failed(
            new IllegalStateException(
              s"[${eventsBySlicesQuery.getClass.getName}] must implement [${classOf[EventTimestampQuery].getName}]"))
      }

    override def loadEnvelope[Evt](
        persistenceId: String,
        sequenceNr: Long): Future[pekko.persistence.query.typed.EventEnvelope[Evt]] =
      eventsBySlicesQuery match {
        case laodEventQuery: LoadEventQuery =>
          laodEventQuery.loadEnvelope(persistenceId, sequenceNr)
        case _ =>
          Future.failed(
            new IllegalStateException(
              s"[${eventsBySlicesQuery.getClass.getName}] must implement [${classOf[LoadEventQuery].getName}]"))
      }
  }

}
