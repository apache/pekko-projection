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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.grpc.GrpcClientSettings
import pekko.grpc.scaladsl.Metadata
import pekko.persistence.typed.ReplicaId
import pekko.projection.grpc.replication.javadsl.{ Replica => JReplica }
import pekko.projection.grpc.replication.scaladsl.{ Replica => SReplica }

import java.util.Optional
import scala.jdk.OptionConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class ReplicaImpl(
    override val replicaId: ReplicaId,
    override val numberOfConsumers: Int,
    override val grpcClientSettings: GrpcClientSettings,
    override val additionalQueryRequestMetadata: Option[org.apache.pekko.grpc.scaladsl.Metadata],
    override val consumersOnClusterRole: Option[String])
    extends JReplica
    with SReplica {

  def getAdditionalQueryRequestMetadata: Optional[Metadata] = additionalQueryRequestMetadata.toJava

  def getConsumersOnClusterRole: Optional[String] = consumersOnClusterRole.toJava

  override def withReplicaId(replicaId: ReplicaId): ReplicaImpl =
    copy(replicaId = replicaId)

  override def withNumberOfConsumers(numberOfConsumers: Int): ReplicaImpl =
    copy(numberOfConsumers = numberOfConsumers)

  override def withGrpcClientSettings(grpcClientSettings: GrpcClientSettings): ReplicaImpl =
    copy(grpcClientSettings = grpcClientSettings)

  /**
   * Scala API: Metadata to include in the requests to the remote Pekko gRPC projection endpoint
   */
  def withAdditionalQueryRequestMetadata(metadata: org.apache.pekko.grpc.scaladsl.Metadata): ReplicaImpl =
    copy(additionalQueryRequestMetadata = Some(metadata))

  /**
   * Java API: Metadata to include in the requests to the remote Pekko gRPC projection endpoint
   */
  def withAdditionalQueryRequestMetadata(metadata: org.apache.pekko.grpc.javadsl.Metadata): ReplicaImpl =
    copy(additionalQueryRequestMetadata = Some(metadata.asScala))

  def withConsumersOnClusterRole(clusterRole: String): ReplicaImpl =
    copy(consumersOnClusterRole = Some(clusterRole))

  override def toScala: SReplica = this

  override def toString: String = s"Replica($replicaId, $numberOfConsumers, ${consumersOnClusterRole.getOrElse("")})"
}
