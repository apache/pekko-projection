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

package org.apache.pekko.projection.grpc.replication.internal

import pekko.Done
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.persistence.query.Offset
import pekko.persistence.query.typed.EventEnvelope
import pekko.projection.BySlicesSourceProvider
import pekko.projection.ProjectionContext
import pekko.projection.ProjectionId
import pekko.projection.grpc.replication.javadsl.{ ReplicationProjectionProvider => JReplicationProjectionProvider }
import pekko.projection.grpc.replication.scaladsl.{ ReplicationProjectionProvider => SReplicationProjectionProvider }
import pekko.projection.internal.ScalaBySlicesSourceProviderAdapter
import pekko.projection.scaladsl.{ AtLeastOnceFlowProjection => SAtLeastOnceFlowProjection }
import pekko.projection.scaladsl.{ SourceProvider => SSourceProvider }
import pekko.stream.scaladsl.{ FlowWithContext => SFlowWithContext }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ReplicationProjectionProviderAdapter {
  def toScala(provider: JReplicationProjectionProvider): SReplicationProjectionProvider = {
    (
        projectionId: ProjectionId,
        sourceProvider: SSourceProvider[Offset, EventEnvelope[AnyRef]],
        replicationFlow: SFlowWithContext[EventEnvelope[AnyRef], ProjectionContext, Done, ProjectionContext, NotUsed],
        system: ActorSystem[_]) =>
      val providerWithSlices = sourceProvider match {
        case withSlices: SSourceProvider[Offset, EventEnvelope[AnyRef]] with BySlicesSourceProvider => withSlices
        case noSlices                                                                               =>
          throw new IllegalArgumentException(
            s"The source provider is required to implement org.apache.pekko.projection.BySlicesSourceProvider but ${noSlices.getClass} does not")
      }
      val javaProjection =
        provider.create(
          projectionId,
          new ScalaBySlicesSourceProviderAdapter(providerWithSlices),
          replicationFlow.asJava,
          system)
      javaProjection match {
        case alsoSProjection: SAtLeastOnceFlowProjection[Offset @unchecked, EventEnvelope[AnyRef] @unchecked] =>
          alsoSProjection

        case other =>
          // FIXME can we really expect that projections always implement both?
          throw new IllegalArgumentException(s"Unsupported type of projection ${other.getClass}")
      }
  }
}
