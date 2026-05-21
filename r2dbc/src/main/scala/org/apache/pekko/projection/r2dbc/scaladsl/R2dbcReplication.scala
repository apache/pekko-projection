/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.scaladsl

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.projection.grpc.replication.scaladsl.ReplicationProjectionProvider
import org.apache.pekko.projection.r2dbc.R2dbcProjectionSettings

object R2dbcReplication {

  /**
   * Creates a projection provider for using R2dbc as backend for the Pekko Projection gRPC transport for Replicated
   * Event Sourcing.
   */
  def apply()(implicit system: ActorSystem[_]): ReplicationProjectionProvider =
    R2dbcProjection.atLeastOnceFlow(
      _,
      Some(R2dbcProjectionSettings(system).withWarnAboutFilteredEventsInFlow(false)),
      _,
      _)(_)

  /**
   * Creates a projection provider for using R2dbc as backend for the Pekko Projection gRPC transport for Replicated
   * Event Sourcing.
   */
  def apply(settings: R2dbcProjectionSettings): ReplicationProjectionProvider =
    R2dbcProjection.atLeastOnceFlow(_, Some(settings.withWarnAboutFilteredEventsInFlow(false)), _, _)(_)

}
