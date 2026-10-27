/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.internal.h2

import java.time.Clock

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.persistence.r2dbc.internal.Sql.DialectInterpolation
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionId
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.R2dbcOffsetStore

/**
 * INTERNAL API
 */
@InternalApi
private[projection] class H2R2dbcOffsetStore(
    projectionId: ProjectionId,
    sourceProvider: Option[BySlicesSourceProvider],
    system: ActorSystem[?],
    settings: R2dbcProjectionSettings,
    r2dbcExecutor: R2dbcExecutor,
    clock: Clock = Clock.systemUTC())
    extends R2dbcOffsetStore(projectionId, sourceProvider, system, settings, r2dbcExecutor, clock) {

  // H2 has no transaction_timestamp(), CURRENT_TIMESTAMP is the start of the transaction
  override lazy val timestampSql: String = "CURRENT_TIMESTAMP"

  override val upsertOffsetSql: String = sql"""
    MERGE INTO $offsetTable
    (projection_name, projection_key, current_offset, manifest, mergeable, last_updated)
    KEY(projection_name, projection_key)
    VALUES (?,?,?,?,?,?)"""

  override val updateManagementStateSql: String = sql"""
    MERGE INTO $managementTable
    (projection_name, projection_key, paused, last_updated)
    KEY(projection_name, projection_key)
    VALUES (?,?,?,?)"""
}
