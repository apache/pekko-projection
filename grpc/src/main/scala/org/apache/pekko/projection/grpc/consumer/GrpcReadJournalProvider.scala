/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.consumer

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.ReadJournalProvider
import pekko.projection.grpc.internal.ProtoAnySerialization
import com.typesafe.config.Config

/**
 * Note that `GrpcReadJournal` should be created with the `GrpcReadJournal` `apply` / `create` factory method
 * and not from configuration via `GrpcReadJournalProvider` when using Protobuf serialization.
 */
final class GrpcReadJournalProvider(system: ExtendedActorSystem, config: Config, cfgPath: String)
    extends ReadJournalProvider {
  override def scaladslReadJournal(): scaladsl.GrpcReadJournal =
    new scaladsl.GrpcReadJournal(system, config, cfgPath)

  override def javadslReadJournal(): javadsl.GrpcReadJournal =
    new javadsl.GrpcReadJournal(
      new scaladsl.GrpcReadJournal(system, config, cfgPath, ProtoAnySerialization.Prefer.Java))
}
