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

package org.apache.pekko.projection.cassandra.scaladsl

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.cassandra.internal.CassandraOffsetStore
import pekko.projection.cassandra.internal.CassandraProjectionImpl
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.AtMostOnce
import pekko.projection.internal.FlowHandlerStrategy
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.NoopStatusObserver
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.scaladsl.AtLeastOnceFlowProjection
import pekko.projection.scaladsl.AtLeastOnceProjection
import pekko.projection.scaladsl.AtMostOnceProjection
import pekko.projection.scaladsl.GroupedProjection
import pekko.projection.scaladsl.Handler
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.scaladsl.FlowWithContext

/**
 * Factories of [[pekko.projection.Projection]] where the offset is stored in Cassandra. The envelope handler can
 * integrate with anything, such as publishing to a message broker, or updating a read model in Cassandra.
 *
 * The envelope handler function can be stateful, with variables and mutable data structures.
 * It is invoked by the `Projection` machinery one envelope at a time and visibility
 * guarantees between the invocations are handled automatically, i.e. no volatile or
 * other concurrency primitives are needed for managing the state.
 */
@ApiMayChange
object CassandraProjection {

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics. It stores the offset in Cassandra
   * after the `handler` has processed the envelope. This means that if the projection is restarted
   * from previously stored offset some envelopes may be processed more than once.
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first.
   * This window can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned
   * `AtLeastOnceCassandraProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.at-least-once`.
   */
  def atLeastOnce[Offset, Envelope](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[Envelope]): AtLeastOnceProjection[Offset, Envelope] =
    new CassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = SingleHandlerStrategy(handler),
      statusObserver = NoopStatusObserver)

  /**
   * Create a [[pekko.projection.Projection]] that groups envelopes and calls the `handler` with a group of `Envelopes`.
   * The envelopes are grouped within a time window, or limited by a number of envelopes,
   * whatever happens first. This window can be defined with [[GroupedProjection.withGroup]] of
   * the returned `GroupedCassandraProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.grouped`.
   *
   * It stores the offset in Cassandra immediately after the `handler` has processed the envelopes, but that
   * is still with at-least-once processing semantics. This means that if the projection is restarted
   * from previously stored offset the previous group of envelopes may be processed more than once.
   */
  def groupedWithin[Offset, Envelope](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[immutable.Seq[Envelope]]): GroupedProjection[Offset, Envelope] =
    new CassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(afterEnvelopes = Some(1), orAfterDuration = Some(Duration.Zero)),
      handlerStrategy = GroupedHandlerStrategy(handler),
      statusObserver = NoopStatusObserver)

  /**
   * Create a [[pekko.projection.Projection]] with a [[FlowWithContext]] as the envelope handler. It has at-least-once processing
   * semantics.
   *
   * The flow should emit a `Done` element for each completed envelope. The offset of the envelope is carried
   * in the context of the `FlowWithContext` and is stored in Cassandra when corresponding `Done` is emitted.
   * Since the offset is stored after processing the envelope it means that if the
   * projection is restarted from previously stored offset then some envelopes may be processed more than once.
   *
   * If the flow filters out envelopes the corresponding offset will not be stored, and such envelope
   * will be processed again if the projection is restarted and no later offset was stored.
   *
   * The flow should not duplicate emitted envelopes (`mapConcat`) with same offset, because then it can result in
   * that the first offset is stored and when the projection is restarted that offset is considered completed even
   * though more of the duplicated enveloped were never processed.
   *
   * The flow must not reorder elements, because the offsets may be stored in the wrong order and
   * and when the projection is restarted all envelopes up to the latest stored offset are considered
   * completed even though some of them may not have been processed. This is the reason the flow is
   * restricted to `FlowWithContext` rather than ordinary `Flow`.
   */
  def atLeastOnceFlow[Offset, Envelope](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: FlowWithContext[Envelope, ProjectionContext, Done, ProjectionContext, _])
      : AtLeastOnceFlowProjection[Offset, Envelope] =
    new CassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = FlowHandlerStrategy(handler),
      statusObserver = NoopStatusObserver)

  /**
   * Create a [[pekko.projection.Projection]] with at-most-once processing semantics. It stores the offset in Cassandra
   * before the `handler` has processed the envelope. This means that if the projection is restarted
   * from previously stored offset one envelope may not have been processed.
   */
  def atMostOnce[Offset, Envelope](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[Envelope]): AtMostOnceProjection[Offset, Envelope] =
    new CassandraProjectionImpl(
      projectionId,
      sourceProvider,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtMostOnce(),
      handlerStrategy = SingleHandlerStrategy(handler),
      statusObserver = NoopStatusObserver)

  /**
   * For testing purposes the projection offset and management tables can be created programmatically.
   * For production, it's recommended to create the table with DDL statements
   * before the system is started.
   */
  def createTablesIfNotExists()(implicit system: ActorSystem[_]): Future[Done] = {
    val offsetStore = new CassandraOffsetStore(system)
    offsetStore.createKeyspaceAndTable()
  }

  @deprecated("Renamed to createTablesIfNotExists", "1.2.0")
  def createOffsetTableIfNotExists()(implicit system: ActorSystem[_]): Future[Done] =
    createTablesIfNotExists()

}
