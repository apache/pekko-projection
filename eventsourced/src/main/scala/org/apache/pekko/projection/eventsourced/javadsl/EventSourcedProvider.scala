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

package org.apache.pekko.projection.eventsourced.javadsl

import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.typesafe.config.Config
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.japi.Pair
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.javadsl.EventsByTagQuery
import pekko.persistence.query.typed.javadsl.EventTimestampQuery
import pekko.persistence.query.typed.javadsl.EventsBySliceQuery
import pekko.persistence.query.typed.javadsl.LoadEventQuery
import pekko.projection.BySlicesSourceProvider
import pekko.projection.eventsourced.EventEnvelope
import pekko.projection.javadsl
import pekko.projection.javadsl.SourceProvider
import pekko.stream.javadsl.Source
import pekko.util.FutureConverters._

@ApiMayChange
object EventSourcedProvider {

  def eventsByTag[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {

    val eventsByTagQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsByTagQuery], readJournalPluginId)

    new EventsByTagSourceProvider(system, eventsByTagQuery, tag)
  }

  def eventsByTag[Event](
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {

    val eventsByTagQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsByTagQuery], readJournalPluginId, readJournalConfig)

    new EventsByTagSourceProvider(system, eventsByTagQuery, tag)
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private class EventsByTagSourceProvider[Event](
      system: ActorSystem[_],
      eventsByTagQuery: EventsByTagQuery,
      tag: String)
      extends javadsl.SourceProvider[Offset, EventEnvelope[Event]] {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offsetAsync: Supplier[CompletionStage[Optional[Offset]]])
        : CompletionStage[Source[EventEnvelope[Event], NotUsed]] = {
      val source: Future[Source[EventEnvelope[Event], NotUsed]] = offsetAsync.get().asScala.map { offsetOpt =>
        eventsByTagQuery
          .eventsByTag(tag, offsetOpt.orElse(NoOffset))
          .map(env => EventEnvelope(env))
      }
      source.asJava
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
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)

    if (!eventsBySlicesQuery.isInstanceOf[EventTimestampQuery])
      throw new IllegalArgumentException(
        s"[${eventsBySlicesQuery.getClass.getName}] with readJournalPluginId " +
        s"[$readJournalPluginId] must implement [${classOf[EventTimestampQuery].getName}]")

    if (!eventsBySlicesQuery.isInstanceOf[LoadEventQuery])
      throw new IllegalArgumentException(
        s"[${eventsBySlicesQuery.getClass.getName}] with readJournalPluginId " +
        s"[$readJournalPluginId] must implement [${classOf[LoadEventQuery].getName}]")

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
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)

    if (!eventsBySlicesQuery.isInstanceOf[EventTimestampQuery])
      throw new IllegalArgumentException(
        s"[${eventsBySlicesQuery.getClass.getName}] with readJournalPluginId " +
        s"[$readJournalPluginId] must implement [${classOf[EventTimestampQuery].getName}]")

    if (!eventsBySlicesQuery.isInstanceOf[LoadEventQuery])
      throw new IllegalArgumentException(
        s"[${eventsBySlicesQuery.getClass.getName}] with readJournalPluginId " +
        s"[$readJournalPluginId] must implement [${classOf[LoadEventQuery].getName}]")

    new EventsBySlicesSourceProvider(eventsBySlicesQuery, entityType, minSlice, maxSlice, system)
  }

  def sliceForPersistenceId(system: ActorSystem[_], readJournalPluginId: String, persistenceId: String): Int =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
      .sliceForPersistenceId(persistenceId)

  def sliceForPersistenceId(
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      persistenceId: String
  ): Int =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)
      .sliceForPersistenceId(persistenceId)

  def sliceRanges(
      system: ActorSystem[_],
      readJournalPluginId: String,
      numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
      .sliceRanges(numberOfRanges)

  def sliceRanges(
      system: ActorSystem[_],
      readJournalPluginId: String,
      readJournalConfig: Config,
      numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)
      .sliceRanges(numberOfRanges)

  /**
   * INTERNAL API
   */
  @InternalApi
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

    override def source(offsetAsync: Supplier[CompletionStage[Optional[Offset]]])
        : CompletionStage[Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed]] = {
      val source: Future[Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed]] =
        offsetAsync.get().asScala.map { offsetOpt =>
          eventsBySlicesQuery
            .eventsBySlices(entityType, minSlice, maxSlice, offsetOpt.orElse(NoOffset))
        }
      source.asJava
    }

    override def extractOffset(envelope: pekko.persistence.query.typed.EventEnvelope[Event]): Offset = envelope.offset

    override def extractCreationTime(envelope: pekko.persistence.query.typed.EventEnvelope[Event]): Long =
      envelope.timestamp

    override def timestampOf(persistenceId: String, sequenceNr: Long): CompletionStage[Optional[Instant]] =
      eventsBySlicesQuery match {
        case timestampQuery: EventTimestampQuery =>
          timestampQuery.timestampOf(persistenceId, sequenceNr)
        case _ =>
          val failed = new CompletableFuture[Optional[Instant]]
          failed.completeExceptionally(
            new IllegalStateException(
              s"[${eventsBySlicesQuery.getClass.getName}] must implement [${classOf[EventTimestampQuery].getName}]"))
          failed.toCompletableFuture
      }

    override def loadEnvelope[Evt](
        persistenceId: String,
        sequenceNr: Long): CompletionStage[pekko.persistence.query.typed.EventEnvelope[Evt]] =
      eventsBySlicesQuery match {
        case loadEventQuery: LoadEventQuery =>
          loadEventQuery.loadEnvelope(persistenceId, sequenceNr)
        case _ =>
          val failed = new CompletableFuture[pekko.persistence.query.typed.EventEnvelope[Evt]]
          failed.completeExceptionally(
            new IllegalStateException(
              s"[${eventsBySlicesQuery.getClass.getName}] must implement [${classOf[LoadEventQuery].getName}]"))
          failed.toCompletableFuture
      }
  }
}
