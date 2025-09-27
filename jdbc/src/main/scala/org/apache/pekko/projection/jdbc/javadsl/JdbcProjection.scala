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

package org.apache.pekko.projection.jdbc.javadsl

import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.ExactlyOnce
import pekko.projection.internal.FlowHandlerStrategy
import pekko.projection.internal.GroupedHandlerAdapter
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.HandlerAdapter
import pekko.projection.internal.NoopStatusObserver
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.internal.SourceProviderAdapter
import pekko.projection.javadsl.AtLeastOnceFlowProjection
import pekko.projection.javadsl.AtLeastOnceProjection
import pekko.projection.javadsl.ExactlyOnceProjection
import pekko.projection.javadsl.GroupedProjection
import pekko.projection.javadsl.Handler
import pekko.projection.javadsl.SourceProvider
import pekko.projection.jdbc.JdbcSession
import pekko.projection.jdbc.internal.GroupedJdbcHandlerAdapter
import pekko.projection.jdbc.internal.JdbcHandlerAdapter
import pekko.projection.jdbc.internal.JdbcProjectionImpl
import pekko.stream.javadsl.FlowWithContext

@ApiMayChange
object JdbcProjection {

  /**
   * Create a [[pekko.projection.Projection]] with exactly-once processing semantics.
   *
   * It stores the offset in a relational database table using JDBC in the same transaction
   * as the user defined `handler`.
   */
  def exactlyOnce[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: Supplier[JdbcHandler[Envelope, S]],
      system: ActorSystem[_]): ExactlyOnceProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val javaSourceProvider = new SourceProviderAdapter(sourceProvider)
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    val adaptedHandler =
      JdbcProjectionImpl.adaptedHandlerForExactlyOnce(
        projectionId,
        javaSourceProvider,
        sessionFactory,
        () => new JdbcHandlerAdapter(handler.get()),
        offsetStore)

    new JdbcProjectionImpl(
      projectionId,
      javaSourceProvider,
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      ExactlyOnce(),
      SingleHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics.
   *
   * It stores the offset in a relational database table using JDBC after the `handler` has processed the envelope.
   * This means that if the projection is restarted from previously stored offset then some elements may be processed
   * more than once.
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first.
   * This window can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned
   * `AtLeastOnceProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.at-least-once`.
   */
  def atLeastOnce[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: Supplier[JdbcHandler[Envelope, S]],
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    val adaptedHandler =
      JdbcProjectionImpl.adaptedHandlerForAtLeastOnce(
        sessionFactory,
        () => new JdbcHandlerAdapter(handler.get()),
        offsetStore)

    new JdbcProjectionImpl(
      projectionId,
      new SourceProviderAdapter(sourceProvider),
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      AtLeastOnce(),
      SingleHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] with at-least-once processing semantics.
   *
   * Compared to [[JdbcProjection.atLeastOnce]] the [[Handler]] is not storing the projected result in the
   * database, but is integrating with something else.
   *
   * It stores the offset in a relational database table using JDBC after the `handler` has processed the envelope.
   * This means that if the projection is restarted from previously stored offset then some elements may be processed
   * more than once.
   *
   * The offset is stored after a time window, or limited by a number of envelopes, whatever happens first.
   * This window can be defined with [[AtLeastOnceProjection.withSaveOffset]] of the returned
   * `AtLeastOnceProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.at-least-once`.
   */
  def atLeastOnceAsync[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: Supplier[Handler[Envelope]],
      system: ActorSystem[_]): AtLeastOnceProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    new JdbcProjectionImpl(
      projectionId,
      new SourceProviderAdapter(sourceProvider),
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      AtLeastOnce(),
      SingleHandlerStrategy(() => HandlerAdapter(handler.get())),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] that groups envelopes and calls the `handler` with a group of `Envelopes`.
   * The envelopes are grouped within a time window, or limited by a number of envelopes,
   * whatever happens first. This window can be defined with [[GroupedProjection.withGroup]] of
   * the returned `GroupedProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.grouped`.
   *
   * It stores the offset in a relational database table using JDBC in the same transaction
   * as the user defined `handler`.
   */
  def groupedWithin[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: Supplier[JdbcHandler[java.util.List[Envelope], S]],
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val javaSourceProvider = new SourceProviderAdapter(sourceProvider)
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    val adaptedHandler =
      JdbcProjectionImpl.adaptedHandlerForGrouped(
        projectionId,
        javaSourceProvider,
        sessionFactory,
        () => new GroupedJdbcHandlerAdapter(handler.get()),
        offsetStore)

    new JdbcProjectionImpl(
      projectionId,
      javaSourceProvider,
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      ExactlyOnce(),
      GroupedHandlerStrategy(adaptedHandler),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] that groups envelopes and calls the `handler` with a group of `Envelopes`.
   * The envelopes are grouped within a time window, or limited by a number of envelopes,
   * whatever happens first. This window can be defined with [[GroupedProjection.withGroup]] of
   * the returned `GroupedProjection`. The default settings for the window is defined in configuration
   * section `pekko.projection.grouped`.
   *
   * Compared to [[JdbcProjection.groupedWithin]] the [[Handler]] is not storing the projected result in the
   * database, but is integrating with something else.
   *
   * It stores the offset in  a relational database table using JDBC immediately after the `handler` has
   * processed the envelopes, but that is still with at-least-once processing semantics. This means that
   * if the projection is restarted from previously stored offset the previous group of envelopes may be
   * processed more than once.
   */
  def groupedWithinAsync[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: Supplier[Handler[java.util.List[Envelope]]],
      system: ActorSystem[_]): GroupedProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val javaSourceProvider = new SourceProviderAdapter(sourceProvider)
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    new JdbcProjectionImpl(
      projectionId,
      javaSourceProvider,
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      AtLeastOnce(afterEnvelopes = Some(1), orAfterDuration = Some(scala.concurrent.duration.Duration.Zero)),
      GroupedHandlerStrategy(() => new GroupedHandlerAdapter(handler.get())),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * Create a [[pekko.projection.Projection]] with a [[FlowWithContext]] as the envelope handler. It has at-least-once processing
   * semantics.
   *
   * The flow should emit a `Done` element for each completed envelope. The offset of the envelope is carried
   * in the context of the `FlowWithContext` and is stored in the database when corresponding `Done` is emitted.
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
  def atLeastOnceFlow[Offset, Envelope, S <: JdbcSession](
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, Envelope],
      sessionCreator: Supplier[S],
      handler: FlowWithContext[Envelope, ProjectionContext, Done, ProjectionContext, _],
      system: ActorSystem[_]): AtLeastOnceFlowProjection[Offset, Envelope] = {

    val sessionFactory = () => sessionCreator.get()
    val javaSourceProvider = new SourceProviderAdapter(sourceProvider)
    val offsetStore = JdbcProjectionImpl.createOffsetStore(sessionFactory)(system)

    new JdbcProjectionImpl(
      projectionId,
      javaSourceProvider,
      sessionFactory = sessionFactory,
      settingsOpt = None,
      restartBackoffOpt = None,
      offsetStrategy = AtLeastOnce(),
      handlerStrategy = FlowHandlerStrategy(handler.asScala),
      NoopStatusObserver,
      offsetStore)
  }

  /**
   * For testing purposes the projection offset and management tables can be created programmatically.
   * For production it's recommended to create the table with DDL statements
   * before the system is started.
   */
  def createTablesIfNotExists[S <: JdbcSession](
      sessionFactory: Supplier[S],
      system: ActorSystem[_]): CompletionStage[Done] =
    JdbcProjectionImpl.createOffsetStore(() => sessionFactory.get())(system).createIfNotExists().asJava

  @deprecated("Renamed to createTablesIfNotExists", "1.2.0")
  def createOffsetTableIfNotExists[S <: JdbcSession](
      sessionFactory: Supplier[S],
      system: ActorSystem[_]): CompletionStage[Done] =
    createTablesIfNotExists(sessionFactory, system)

  /**
   * For testing purposes the projection offset and management tables can be dropped programmatically.
   */
  def dropTablesIfExists[S <: JdbcSession](sessionFactory: Supplier[S], system: ActorSystem[_]): CompletionStage[Done] =
    JdbcProjectionImpl.createOffsetStore(() => sessionFactory.get())(system).dropIfExists().asJava

  @deprecated("Renamed to dropTablesIfExists", "1.2.0")
  def dropOffsetTableIfExists[S <: JdbcSession](
      sessionFactory: Supplier[S],
      system: ActorSystem[_]): CompletionStage[Done] =
    dropTablesIfExists(sessionFactory, system)
}
