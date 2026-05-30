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

package org.apache.pekko.projection.r2dbc.scaladsl

import scala.collection.immutable

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.persistence.r2dbc.ConnectionFactoryProvider
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.ExactlyOnce
import pekko.projection.internal.FlowHandlerStrategy
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.NoopStatusObserver
import pekko.projection.internal.OffsetStoredByHandler
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.R2dbcProjectionImpl
import pekko.projection.scaladsl.AtLeastOnceFlowProjection
import pekko.projection.scaladsl.AtLeastOnceProjection
import pekko.projection.scaladsl.ExactlyOnceProjection
import pekko.projection.scaladsl.GroupedProjection
import pekko.projection.scaladsl.Handler
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.scaladsl.FlowWithContext
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.r2dbc.spi.ConnectionFactory

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
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[Envelope])(implicit
      system: ActorSystem[_]): ExactlyOnceProjection[Offset, Envelope] =
    exactlyOnce(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def exactlyOnce[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[Envelope])(implicit
      system: ActorSystem[_]): ExactlyOnceProjection[Offset, Envelope] = {
    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)
    val r2dbcExecutor = new R2dbcExecutor(connFactory, R2dbcProjectionImpl.log, r2dbcSettings.logDbCallsExceeding)(
      system.executionContext,
      system)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForExactlyOnce(sourceProvider, handler, offsetStore, r2dbcExecutor)(
        system.executionContext,
        system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = ExactlyOnce(),
      handlerStrategy = SingleHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics.
   *
   * It stores the offset in a relational database table using R2DBC after the `handler` has processed the envelope.
   * This means that if the projection is restarted from previously stored offset then some elements may be processed
   * more than once.
   *
   * The [[R2dbcHandler.process()]] in `handler` will be wrapped in a transaction. The transaction will be committed
   * after invoking [[R2dbcHandler.process()]].
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first. This window
   * can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned `AtLeastOnceProjection`. The default
   * settings for the window is defined in configuration section `pekko.projection.at-least-once`.
   */
  def atLeastOnce[Offset, Envelope](
      projectionId: ProjectionId,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[Envelope])(implicit
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] =
    atLeastOnce(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def atLeastOnce[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[Envelope])(implicit
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {

    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)
    val r2dbcExecutor = new R2dbcExecutor(connFactory, R2dbcProjectionImpl.log, r2dbcSettings.logDbCallsExceeding)(
      system.executionContext,
      system)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForAtLeastOnce(sourceProvider, handler, offsetStore, r2dbcExecutor)(
        system.executionContext,
        system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = SingleHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
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
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[Envelope])(implicit system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] =
    atLeastOnceAsync(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def atLeastOnceAsync[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[Envelope])(implicit system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {

    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForAtLeastOnceAsync(sourceProvider, handler, offsetStore)(
        system.executionContext,
        system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = SingleHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
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
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[immutable.Seq[Envelope]])(implicit
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] =
    groupedWithin(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def groupedWithin[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => R2dbcHandler[immutable.Seq[Envelope]])(implicit
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {

    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)
    val r2dbcExecutor = new R2dbcExecutor(connFactory, R2dbcProjectionImpl.log, r2dbcSettings.logDbCallsExceeding)(
      system.executionContext,
      system)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForGrouped(sourceProvider, handler, offsetStore, r2dbcExecutor)(
        system.executionContext,
        system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = ExactlyOnce(),
      handlerStrategy = GroupedHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
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
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[immutable.Seq[Envelope]])(implicit
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] =
    groupedWithinAsync(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def groupedWithinAsync[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: () => Handler[immutable.Seq[Envelope]])(implicit
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {

    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForGroupedAsync(sourceProvider, handler, offsetStore)(
        system.executionContext,
        system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = OffsetStoredByHandler(),
      handlerStrategy = GroupedHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
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
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: FlowWithContext[Envelope, ProjectionContext, Done, ProjectionContext, _])(implicit
      system: ActorSystem[_]): AtLeastOnceFlowProjection[Offset, Envelope] =
    atLeastOnceFlow(projectionId, ConfigFactory.empty(), settings, sourceProvider, handler)

  /** @since 2.0.0 */
  def atLeastOnceFlow[Offset, Envelope](
      projectionId: ProjectionId,
      config: Config,
      settings: Option[R2dbcProjectionSettings],
      sourceProvider: SourceProvider[Offset, Envelope],
      handler: FlowWithContext[Envelope, ProjectionContext, Done, ProjectionContext, _])(implicit
      system: ActorSystem[_]): AtLeastOnceFlowProjection[Offset, Envelope] = {

    val r2dbcSettings = settings.getOrElse(R2dbcProjectionSettings(config, system))
    val connFactory = connectionFactory(system, config, r2dbcSettings)
    val offsetStore =
      R2dbcProjectionImpl.createOffsetStore(
        projectionId,
        timestampOffsetBySlicesSourceProvider(sourceProvider),
        r2dbcSettings,
        connFactory)

    val adaptedHandler =
      R2dbcProjectionImpl.adaptedHandlerForFlow(sourceProvider, handler, offsetStore, r2dbcSettings)(system)

    new R2dbcProjectionImpl(
      projectionId,
      r2dbcSettings,
      settingsOpt = None,
      sourceProvider,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = FlowHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
  }

  private def connectionFactory(system: ActorSystem[_], config: Config, r2dbcSettings: R2dbcProjectionSettings)
      : ConnectionFactory = {
    ConnectionFactoryProvider(system).connectionFactoryFor(r2dbcSettings.useConnectionFactory, config)
  }

  private def timestampOffsetBySlicesSourceProvider(
      sourceProvider: SourceProvider[_, _]): Option[BySlicesSourceProvider] = {
    sourceProvider match {
      case provider: BySlicesSourceProvider => Some(provider)
      case _                                => None // source provider is not using slices
    }
  }

}
