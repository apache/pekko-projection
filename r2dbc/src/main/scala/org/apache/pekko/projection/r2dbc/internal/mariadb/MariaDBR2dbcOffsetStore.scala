/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.projection.r2dbc.internal.mariadb

import java.time.Clock

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.persistence.r2dbc.internal.Sql.DialectInterpolation
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionId
import pekko.projection.r2dbc.R2dbcProjectionSettings
import pekko.projection.r2dbc.internal.mysql.MySQLR2dbcOffsetStore

/**
 * INTERNAL API
 *
 * MariaDB uses the MySQL offset store, except for the upserts: MariaDB doesn't support the `AS excluded` row alias of
 * MySQL, so the inserted values are referenced with `VALUES()`. `Instant` values are converted by the connection
 * factory of pekko-persistence-r2dbc, since the MariaDB driver doesn't support them.
 */
@InternalApi
private[projection] class MariaDBR2dbcOffsetStore(
    projectionId: ProjectionId,
    sourceProvider: Option[BySlicesSourceProvider],
    system: ActorSystem[?],
    settings: R2dbcProjectionSettings,
    r2dbcExecutor: R2dbcExecutor,
    clock: Clock = Clock.systemUTC())
    extends MySQLR2dbcOffsetStore(projectionId, sourceProvider, system, settings, r2dbcExecutor, clock) {

  override val upsertOffsetSql: String = sql"""
    INSERT INTO $offsetTable
    (projection_name, projection_key, current_offset, manifest, mergeable, last_updated)
    VALUES (?,?,?,?,?,?)
    ON DUPLICATE KEY UPDATE
    current_offset = VALUES(current_offset),
    manifest = VALUES(manifest),
    mergeable = VALUES(mergeable),
    last_updated = VALUES(last_updated)"""

  override val updateManagementStateSql: String = sql"""
    INSERT INTO $managementTable
    (projection_name, projection_key, paused, last_updated)
    VALUES (?,?,?,?)
    ON DUPLICATE KEY UPDATE
    paused = VALUES(paused),
    last_updated = VALUES(last_updated)"""
}
