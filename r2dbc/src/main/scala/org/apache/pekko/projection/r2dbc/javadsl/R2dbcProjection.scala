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

package org.apache.pekko.projection.r2dbc.javadsl

import java.util.Optional
import java.util.function.Supplier

import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.internal.GroupedHandlerAdapter
import pekko.projection.internal.HandlerAdapter
import pekko.projection.internal.SourceProviderAdapter
import pekko.projection.javadsl.AtLeastOnceFlowProjection
import pekko.projection.javadsl.AtLeastOnceProjection
import pekko.projection.javadsl.ExactlyOnceProjection
import pekko.projection.javadsl.GroupedProjection
import pekko.projection.javadsl.Handler
import pekko.projection.javadsl.SourceProvider
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.BySliceSourceProviderAdapter
import pekko.projection.r2dbc.internal.R2dbcGroupedHandlerAdapter
import pekko.projection.r2dbc.internal.R2dbcHandlerAdapter
import pekko.projection.r2dbc.scaladsl
import pekko.stream.javadsl.FlowWithContext

@ApiMayChange
object R2dbcProjection {

  /**
   * Create a [[pekko.projection.Projection]] with exactly-once processing semantics.
   *
   * It stores the offset in a relational database table using R2DBC in the same transaction as the user defined
   * `handler`.
   */
  def exactlyOnce[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: Supplier[R2dbcHandler[Envelope]],
      system: ActorSystem[_]): ExactlyOnceProjection[Offset, Envelope] = {
    scaladsl.R2dbcProjection
      .exactlyOnce[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        () => new R2dbcHandlerAdapter(handler.get()))(system)
      .asInstanceOf[ExactlyOnceProjection[Offset, Envelope]]
  }

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics.
   *
   * It stores the offset in a relational database table using R2DBC after the `handler` has processed the envelope.
   * This means that if the projection is restarted from previously stored offset then some elements may be processed
   * more than once.
   *
   * The [[R2dbcHandler.process]] in `handler` will be wrapped in a transaction. The transaction will be committed after
   * invoking [[R2dbcHandler.process]].
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first. This window
   * can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned `AtLeastOnceProjection`. The default
   * settings for the window is defined in configuration section `pekko.projection.at-least-once`.
   */
  def atLeastOnce[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: Supplier[R2dbcHandler[Envelope]],
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {
    scaladsl.R2dbcProjection
      .atLeastOnce[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        () => new R2dbcHandlerAdapter(handler.get()))(system)
      .asInstanceOf[AtLeastOnceProjection[Offset, Envelope]]
  }

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics.
   *
   * Compared to [[R2dbcProjection.atLeastOnce]] the [[Handler]] is not storing the projected result in the database,
   * but is integrating with something else.
   *
   * It stores the offset in a relational database table using R2DBC after the `handler` has processed the envelope.
   * This means that if the projection is restarted from previously stored offset then some elements may be processed
   * more than once.
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first. This window
   * can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned `AtLeastOnceProjection`. The default
   * settings for the window is defined in configuration section `pekko.projection.at-least-once`.
   */
  def atLeastOnceAsync[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: Supplier[Handler[Envelope]],
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {

    scaladsl.R2dbcProjection
      .atLeastOnceAsync[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        () => HandlerAdapter(handler.get()))(system)
      .asInstanceOf[AtLeastOnceProjection[Offset, Envelope]]
  }

  /**
   * Create a [[pekko.projection.Projection]] that groups envelopes and calls the `handler` with a group of `Envelopes`.
   * The envelopes are grouped within a time window, or limited by a number of envelopes, whatever happens first. This
   * window can be defined with [[GroupedProjection.withGroup]] of the returned `GroupedProjection`. The default
   * settings for the window is defined in configuration section `pekko.projection.grouped`.
   *
   * It stores the offset in a relational database table using R2DBC in the same transaction as the user defined
   * `handler`.
   */
  def groupedWithin[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: Supplier[R2dbcHandler[java.util.List[Envelope]]],
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {
    scaladsl.R2dbcProjection
      .groupedWithin[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        () => new R2dbcGroupedHandlerAdapter(handler.get()))(system)
      .asInstanceOf[GroupedProjection[Offset, Envelope]]
  }

  /**
   * Create a [[pekko.projection.Projection]] that groups envelopes and calls the `handler` with a group of `Envelopes`.
   * The envelopes are grouped within a time window, or limited by a number of envelopes, whatever happens first. This
   * window can be defined with [[GroupedProjection.withGroup]] of the returned `GroupedProjection`. The default
   * settings for the window is defined in configuration section `pekko.projection.grouped`.
   *
   * Compared to [[R2dbcProjection.groupedWithin]] the [[Handler]] is not storing the projected result in the database,
   * but is integrating with something else.
   *
   * It stores the offset in a relational database table using R2DBC immediately after the `handler` has processed the
   * envelopes, but that is still with at-least-once processing semantics. This means that if the projection is
   * restarted from previously stored offset the previous group of envelopes may be processed more than once.
   */
  def groupedWithinAsync[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: Supplier[Handler[java.util.List[Envelope]]],
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {
    scaladsl.R2dbcProjection
      .groupedWithinAsync[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        () => new GroupedHandlerAdapter(handler.get()))(system)
      .asInstanceOf[GroupedProjection[Offset, Envelope]]
  }

  /**
   * Create a [[pekko.projection.Projection]] with a [[FlowWithContext]] as the envelope handler. It has at-least-once
   * processing semantics.
   *
   * The flow should emit a `Done` element for each completed envelope. The offset of the envelope is carried in the
   * context of the `FlowWithContext` and is stored in Cassandra when corresponding `Done` is emitted. Since the offset
   * is stored after processing the envelope it means that if the projection is restarted from previously stored offset
   * then some envelopes may be processed more than once.
   *
   * If the flow filters out envelopes the corresponding offset will not be stored, and such envelope will be processed
   * again if the projection is restarted and no later offset was stored.
   *
   * The flow should not duplicate emitted envelopes (`mapConcat`) with same offset, because then it can result in that
   * the first offset is stored and when the projection is restarted that offset is considered completed even though
   * more of the duplicated enveloped were never processed.
   *
   * The flow must not reorder elements, because the offsets may be stored in the wrong order and and when the
   * projection is restarted all envelopes up to the latest stored offset are considered completed even though some of
   * them may not have been processed. This is the reason the flow is restricted to `FlowWithContext` rather than
   * ordinary `Flow`.
   */
  def atLeastOnceFlow[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Optional[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: FlowWithContext[Envelope, ProjectionContext, Done, ProjectionContext, _],
      system: ActorSystem[_]): AtLeastOnceFlowProjection[Offset, Envelope] = {
    scaladsl.R2dbcProjection
      .atLeastOnceFlow[Offset, Envelope](
        projectionId,
        settings.toScala,
        adaptSourceProvider(sourceProvider),
        handler.asScala)(system)
      .asInstanceOf[AtLeastOnceFlowProjection[Offset, Envelope]]
  }

  private def adaptSourceProvider[Offset, Envelope](sourceProvider: SourceProvider[Offset, Envelope]) =
    sourceProvider match {
      case _: BySlicesSourceProvider => new BySliceSourceProviderAdapter(sourceProvider)
      case _                         => new SourceProviderAdapter(sourceProvider)
    }

}
