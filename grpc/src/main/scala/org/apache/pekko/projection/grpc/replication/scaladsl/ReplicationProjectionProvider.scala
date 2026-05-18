/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.replication.scaladsl

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.persistence.query.Offset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.ProjectionContext
import org.apache.pekko.projection.ProjectionId
import org.apache.pekko.projection.scaladsl.AtLeastOnceFlowProjection
import org.apache.pekko.projection.scaladsl.SourceProvider
import org.apache.pekko.stream.scaladsl.FlowWithContext

/**
 * Factory/function for creating the projection where offsets are kept track of for the replication streams
 */
@ApiMayChange
trait ReplicationProjectionProvider {
  def apply(
      projectionId: ProjectionId,
      sourceProvider: SourceProvider[Offset, EventEnvelope[AnyRef]],
      replicationFlow: FlowWithContext[EventEnvelope[AnyRef], ProjectionContext, Done, ProjectionContext, NotUsed],
      system: ActorSystem[_]): AtLeastOnceFlowProjection[Offset, EventEnvelope[AnyRef]]
}
