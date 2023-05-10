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

package org.apache.pekko.projection

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.projection.internal.ActorHandlerInit
import pekko.projection.internal.ManagementState
import pekko.projection.internal.ProjectionSettings
import pekko.stream.scaladsl.RestartSource
import pekko.stream.scaladsl.Source

/**
 * The core abstraction in Pekko Projections.
 *
 * A projection instance may share the same name and [[Envelope]], but must have a unique key. The key is used
 * to achieve processing parallelism for a projection.
 *
 * For example, many projections may share the same name "user-events-projection", but can process events for
 * different sharded entities within Pekko Cluster, where key could be the Pekko Cluster shardId.
 *
 * @tparam Envelope The envelope type of the projection.
 */
@ApiMayChange
trait Projection[Envelope] {

  def projectionId: ProjectionId

  def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double): Projection[Envelope]

  def withRestartBackoff(
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      maxRestarts: Int): Projection[Envelope]

  /**
   * Java API
   */
  def withRestartBackoff(
      minBackoff: java.time.Duration,
      maxBackoff: java.time.Duration,
      randomFactor: Double): Projection[Envelope]

  /**
   * Java API
   */
  def withRestartBackoff(
      minBackoff: java.time.Duration,
      maxBackoff: java.time.Duration,
      randomFactor: Double,
      maxRestarts: Int): Projection[Envelope]

  def statusObserver: StatusObserver[Envelope]

  def withStatusObserver(observer: StatusObserver[Envelope]): Projection[Envelope]

  /**
   * INTERNAL API
   *
   * This method returns the projection Source mapped with user 'handler' function, but before any sink attached.
   * This is mainly intended to be used by the TestKit allowing it to attach a TestSink to it.
   */
  @InternalApi
  private[projection] def mappedSource()(implicit system: ActorSystem[_]): Source[Done, Future[Done]]

  /**
   * INTERNAL API
   */
  @InternalApi private[projection] def actorHandlerInit[T]: Option[ActorHandlerInit[T]]

  /**
   * INTERNAL API
   * Return a RunningProjection
   */
  @InternalApi
  private[projection] def run()(implicit system: ActorSystem[_]): RunningProjection

}

/**
 * INTERNAL API
 *
 * Helper to wrap the projection source with a RestartSource using the provided settings.
 */
@InternalApi
private[projection] object RunningProjection {

  /**
   * When stopping an projection the retry mechanism is aborted via this exception.
   */
  case object AbortProjectionException extends RuntimeException("Projection aborted.") with NoStackTrace

  def withBackoff(source: () => Source[Done, _], settings: ProjectionSettings): Source[Done, _] = {
    RestartSource
      .onFailuresWithBackoff(settings.restartBackoff) { () =>
        source()
          .recoverWithRetries(1,
            {
              case AbortProjectionException => Source.empty // don't restart
            })
      }
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[projection] trait RunningProjection {

  /**
   * Stop the projection if it's running.
   * @return Future[Done] - the returned Future should return the stream materialized value.
   */
  def stop(): Future[Done]
}

/**
 * INTERNAL API
 */
@InternalApi
private[projection] trait RunningProjectionManagement[Offset] {
  def getOffset(): Future[Option[Offset]]
  def setOffset(offset: Option[Offset]): Future[Done]
  def getManagementState(): Future[Option[ManagementState]]
  def setPaused(paused: Boolean): Future[Done]
}
