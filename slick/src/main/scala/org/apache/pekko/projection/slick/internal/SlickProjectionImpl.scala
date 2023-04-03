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

package org.apache.pekko.projection.slick.internal

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.event.LoggingAdapter
import pekko.projection.HandlerRecoveryStrategy
import pekko.projection.ProjectionId
import pekko.projection.RunningProjectionManagement
import pekko.projection.RunningProjection
import pekko.projection.RunningProjection.AbortProjectionException
import pekko.projection.StatusObserver
import pekko.projection.internal.ActorHandlerInit
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.AtMostOnce
import pekko.projection.internal.ExactlyOnce
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.HandlerStrategy
import pekko.projection.internal.InternalProjection
import pekko.projection.internal.InternalProjectionState
import pekko.projection.internal.ManagementState
import pekko.projection.internal.OffsetStrategy
import pekko.projection.internal.ProjectionSettings
import pekko.projection.internal.SettingsImpl
import pekko.projection.scaladsl.AtLeastOnceFlowProjection
import pekko.projection.scaladsl.AtLeastOnceProjection
import pekko.projection.scaladsl.ExactlyOnceProjection
import pekko.projection.scaladsl.GroupedProjection
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.RestartSettings
import pekko.stream.scaladsl.Source
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

/**
 * INTERNAL API
 */
@InternalApi
private[projection] class SlickProjectionImpl[Offset, Envelope, P <: JdbcProfile](
    val projectionId: ProjectionId,
    sourceProvider: SourceProvider[Offset, Envelope],
    databaseConfig: DatabaseConfig[P],
    settingsOpt: Option[ProjectionSettings],
    restartBackoffOpt: Option[RestartSettings],
    val offsetStrategy: OffsetStrategy,
    handlerStrategy: HandlerStrategy,
    override val statusObserver: StatusObserver[Envelope],
    offsetStore: SlickOffsetStore[P])
    extends ExactlyOnceProjection[Offset, Envelope]
    with GroupedProjection[Offset, Envelope]
    with AtLeastOnceProjection[Offset, Envelope]
    with AtLeastOnceFlowProjection[Offset, Envelope]
    with SettingsImpl[SlickProjectionImpl[Offset, Envelope, P]]
    with InternalProjection {

  private def copy(
      settingsOpt: Option[ProjectionSettings] = this.settingsOpt,
      restartBackoffOpt: Option[RestartSettings] = this.restartBackoffOpt,
      offsetStrategy: OffsetStrategy = this.offsetStrategy,
      handlerStrategy: HandlerStrategy = this.handlerStrategy,
      statusObserver: StatusObserver[Envelope] = this.statusObserver): SlickProjectionImpl[Offset, Envelope, P] =
    new SlickProjectionImpl(
      projectionId,
      sourceProvider,
      databaseConfig,
      settingsOpt,
      restartBackoffOpt,
      offsetStrategy,
      handlerStrategy,
      statusObserver,
      offsetStore)

  /*
   * Build the final ProjectionSettings to use, if currently set to None fallback to values in config file
   */
  private def settingsOrDefaults(implicit system: ActorSystem[_]): ProjectionSettings = {
    val settings = settingsOpt.getOrElse(ProjectionSettings(system))
    restartBackoffOpt match {
      case None    => settings
      case Some(r) => settings.copy(restartBackoff = r)
    }
  }

  override def withRestartBackoffSettings(restartBackoff: RestartSettings): SlickProjectionImpl[Offset, Envelope, P] =
    copy(restartBackoffOpt = Some(restartBackoff))

  /**
   * Settings for AtLeastOnceSlickProjection
   */
  override def withSaveOffset(
      afterEnvelopes: Int,
      afterDuration: FiniteDuration): SlickProjectionImpl[Offset, Envelope, P] = {

    // safe cast: withSaveOffset is only available to AtLeastOnceProjection
    val atLeastOnce = offsetStrategy.asInstanceOf[AtLeastOnce]

    copy(offsetStrategy =
      atLeastOnce.copy(afterEnvelopes = Some(afterEnvelopes), orAfterDuration = Some(afterDuration)))
  }

  /**
   * Settings for GroupedSlickProjection
   */
  override def withGroup(
      groupAfterEnvelopes: Int,
      groupAfterDuration: FiniteDuration): SlickProjectionImpl[Offset, Envelope, P] = {

    // safe cast: withGroup is only available to GroupedProjections
    val groupedHandler = handlerStrategy.asInstanceOf[GroupedHandlerStrategy[Envelope]]

    copy(handlerStrategy =
      groupedHandler.copy(afterEnvelopes = Some(groupAfterEnvelopes), orAfterDuration = Some(groupAfterDuration)))
  }

  /**
   * Settings for AtLeastOnceSlickProjection and ExactlyOnceSlickProjection
   */
  override def withRecoveryStrategy(
      recoveryStrategy: HandlerRecoveryStrategy): SlickProjectionImpl[Offset, Envelope, P] = {
    val newStrategy =
      offsetStrategy match {
        case s: ExactlyOnce => s.copy(recoveryStrategy = Some(recoveryStrategy))
        case s: AtLeastOnce => s.copy(recoveryStrategy = Some(recoveryStrategy))
        // NOTE: AtMostOnce has its own withRecoveryStrategy variant
        // this method is not available for AtMostOnceProjection
        case s: AtMostOnce => s
      }
    copy(offsetStrategy = newStrategy)
  }

  override def withStatusObserver(observer: StatusObserver[Envelope]): SlickProjectionImpl[Offset, Envelope, P] =
    copy(statusObserver = observer)

  private[projection] def actorHandlerInit[T]: Option[ActorHandlerInit[T]] =
    handlerStrategy.actorHandlerInit

  /**
   * INTERNAL API
   * Return a RunningProjection
   */
  @InternalApi
  override private[projection] def run()(implicit system: ActorSystem[_]): RunningProjection = {
    new SlickInternalProjectionState(settingsOrDefaults).newRunningInstance()
  }

  /**
   * INTERNAL API
   *
   * This method returns the projection Source mapped with user 'handler' function, but before any sink attached.
   * This is mainly intended to be used by the TestKit allowing it to attach a TestSink to it.
   */
  override private[projection] def mappedSource()(implicit system: ActorSystem[_]): Source[Done, Future[Done]] =
    new SlickInternalProjectionState(settingsOrDefaults).mappedSource()

  /*
   * INTERNAL API
   * This internal class will hold the KillSwitch that is needed
   * when building the mappedSource and when running the projection (to stop)
   */
  private class SlickInternalProjectionState(settings: ProjectionSettings)(implicit val system: ActorSystem[_])
      extends InternalProjectionState[Offset, Envelope](
        projectionId,
        sourceProvider,
        offsetStrategy,
        handlerStrategy,
        statusObserver,
        settings) {

    implicit val executionContext: ExecutionContext = system.executionContext
    override val logger: LoggingAdapter = Logging(system.classicSystem, this.getClass)

    override def readPaused(): Future[Boolean] =
      offsetStore.readManagementState(projectionId).map(_.exists(_.paused))

    override def readOffsets(): Future[Option[Offset]] =
      offsetStore.readOffset(projectionId)

    override def saveOffset(projectionId: ProjectionId, offset: Offset): Future[Done] =
      databaseConfig.db.run(offsetStore.saveOffset(projectionId, offset)).map(_ => Done)

    private[projection] def newRunningInstance(): RunningProjection =
      new SlickRunningProjection(RunningProjection.withBackoff(() => mappedSource(), settings), this)

  }

  private class SlickRunningProjection(source: Source[Done, _], projectionState: SlickInternalProjectionState)(
      implicit system: ActorSystem[_])
      extends RunningProjection
      with RunningProjectionManagement[Offset] {

    private implicit val executionContext: ExecutionContext = system.executionContext

    private val streamDone = source.run()

    override def stop(): Future[Done] = {
      projectionState.killSwitch.shutdown()
      // if the handler is retrying it will be aborted by this,
      // otherwise the stream would not be completed by the killSwitch until after all retries
      projectionState.abort.failure(AbortProjectionException)
      streamDone
    }

    // RunningProjectionManagement
    override def getOffset(): Future[Option[Offset]] = {
      offsetStore.readOffset(projectionId)
    }

    // RunningProjectionManagement
    override def setOffset(offset: Option[Offset]): Future[Done] = {
      offset match {
        case Some(o) =>
          val dbio = offsetStore.saveOffset(projectionId, o)
          databaseConfig.db.run(dbio).map(_ => Done)
        case None =>
          val dbio = offsetStore.clearOffset(projectionId)
          databaseConfig.db.run(dbio).map(_ => Done)
      }
    }

    // RunningProjectionManagement
    override def getManagementState(): Future[Option[ManagementState]] =
      offsetStore.readManagementState(projectionId)

    // RunningProjectionManagement
    override def setPaused(paused: Boolean): Future[Done] =
      offsetStore.savePaused(projectionId, paused)
  }

}
