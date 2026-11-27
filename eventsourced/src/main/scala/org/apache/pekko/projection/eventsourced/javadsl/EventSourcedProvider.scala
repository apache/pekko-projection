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
import java.util.Objects
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters._

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
import pekko.projection.internal.CanTriggerReplay
import pekko.projection.javadsl
import pekko.projection.javadsl.SourceProvider
import pekko.stream.javadsl.Source

@ApiMayChange
object EventSourcedProvider {

  def eventsByTag[Event](
      system: ActorSystem[?],
      readJournalPluginId: String,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {
    val eventsByTagQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsByTagQuery], readJournalPluginId)
    eventsByTag(system, eventsByTagQuery, tag)
  }

  def eventsByTag[Event](
      system: ActorSystem[?],
      readJournalPluginId: String,
      readJournalConfig: Config,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {
    val eventsByTagQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsByTagQuery], readJournalPluginId, readJournalConfig)
    eventsByTag(system, eventsByTagQuery, tag)
  }

  def eventsByTag[Event](
      system: ActorSystem[?],
      eventsByTagQuery: EventsByTagQuery,
      tag: String): SourceProvider[Offset, EventEnvelope[Event]] = {
    new EventsByTagSourceProvider(system, eventsByTagQuery, tag)
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private class EventsByTagSourceProvider[Event](
      system: ActorSystem[?],
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
      system: ActorSystem[?],
      readJournalPluginId: String,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
    eventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice)
  }

  /**
   * Create an events-by-slices source with a fallback query offset when the projection has no stored offset.
   * See the overload that takes an EventsBySliceQuery for the fallback semantics.
   *
   * @since 2.0.0
   */
  def eventsBySlices[Event](
      system: ActorSystem[?],
      readJournalPluginId: String,
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      initialOffset: Supplier[? <: CompletionStage[? <: Offset]])
      : SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
    eventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice, initialOffset)
  }

  def eventsBySlices[Event](
      system: ActorSystem[?],
      readJournalPluginId: String,
      readJournalConfig: Config,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)
    eventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice)
  }

  /**
   * Create an events-by-slices source with a fallback query offset when the projection has no stored offset.
   * See the overload that takes an EventsBySliceQuery for the fallback semantics.
   *
   * @since 2.0.0
   */
  def eventsBySlices[Event](
      system: ActorSystem[?],
      readJournalPluginId: String,
      readJournalConfig: Config,
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      initialOffset: Supplier[? <: CompletionStage[? <: Offset]])
      : SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val eventsBySlicesQuery =
      PersistenceQuery(system).getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)
    eventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice, initialOffset)
  }

  def eventsBySlices[Event](
      system: ActorSystem[?],
      eventsBySlicesQuery: EventsBySliceQuery,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] =
    createEventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice, None)

  /**
   * Create an events-by-slices source with a fallback query offset.
   *
   * The supplier is called only when reading the stored offset succeeds but finds no offset. It can be called
   * again after a restart if no offset has been stored. A supplier failure fails source creation; it is not
   * treated as NoOffset. The supplied offset is passed to the query, not stored as a projection checkpoint.
   *
   * @since 2.0.0
   */
  def eventsBySlices[Event](
      system: ActorSystem[?],
      eventsBySlicesQuery: EventsBySliceQuery,
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      initialOffset: Supplier[? <: CompletionStage[? <: Offset]])
      : SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    val readInitialOffset: () => Future[Offset] = () => {
      val stage: CompletionStage[? <: Offset] = initialOffset.get()
      stage.asScala
    }
    createEventsBySlices(system, eventsBySlicesQuery, entityType, minSlice, maxSlice, Some(readInitialOffset))
  }

  private def createEventsBySlices[Event](
      system: ActorSystem[?],
      eventsBySlicesQuery: EventsBySliceQuery,
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      initialOffset: Option[() => Future[Offset]])
      : SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]] = {
    eventsBySlicesQuery match {
      case query: EventsBySliceQuery with CanTriggerReplay =>
        new EventsBySlicesSourceProvider[Event](
          eventsBySlicesQuery, entityType, minSlice, maxSlice, system, initialOffset)
          with CanTriggerReplay {
          private[pekko] override def triggerReplay(persistenceId: String, fromSeqNr: Long): Unit =
            query.triggerReplay(persistenceId, fromSeqNr)
        }
      case _ =>
        new EventsBySlicesSourceProvider(eventsBySlicesQuery, entityType, minSlice, maxSlice, system, initialOffset)
    }
  }

  def sliceForPersistenceId(system: ActorSystem[?], readJournalPluginId: String, persistenceId: String): Int =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
      .sliceForPersistenceId(persistenceId)

  /** @since 2.0.0 */
  def sliceForPersistenceId(
      system: ActorSystem[?],
      readJournalPluginId: String,
      readJournalConfig: Config,
      persistenceId: String
  ): Int =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId, readJournalConfig)
      .sliceForPersistenceId(persistenceId)

  def sliceRanges(
      system: ActorSystem[?],
      readJournalPluginId: String,
      numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] =
    PersistenceQuery(system)
      .getReadJournalFor(classOf[EventsBySliceQuery], readJournalPluginId)
      .sliceRanges(numberOfRanges)

  /** @since 2.0.0 */
  def sliceRanges(
      system: ActorSystem[?],
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
      system: ActorSystem[?],
      initialOffset: Option[() => Future[Offset]])
      extends SourceProvider[Offset, pekko.persistence.query.typed.EventEnvelope[Event]]
      with BySlicesSourceProvider
      with EventTimestampQuery
      with LoadEventQuery {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offsetAsync: Supplier[CompletionStage[Optional[Offset]]])
        : CompletionStage[Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed]] = {
      def sourceAt(offset: Offset) = eventsBySlicesQuery.eventsBySlices[Event](entityType, minSlice, maxSlice, offset)

      val storedOffset = offsetAsync.get().asScala
      val source = initialOffset match {
        case None          => storedOffset.map(offset => sourceAt(offset.orElse(NoOffset)))
        case Some(initial) =>
          storedOffset.flatMap { offset =>
            if (offset.isPresent) Future.successful(sourceAt(offset.get()))
            else initial().map(value => sourceAt(Objects.requireNonNull(value, "initialOffset result")))
          }
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
