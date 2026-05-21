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

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.grpc.GrpcClientSettings
import pekko.persistence.typed.ReplicaId
import pekko.projection.grpc.replication.internal.ReplicaImpl

object Replica {

  /**
   * Describes a specific remote replica, how to connect to identify, connect and consume events from it.
   *
   * @param replicaId          The unique logical identifier of the replica
   * @param numberOfConsumers  How many consumers to start for consuming events from this replica
   * @param grpcClientSettings Settings for how to connect to the replica, host, port, TLS etc.
   */
  def apply(replicaId: ReplicaId, numberOfConsumers: Int, grpcClientSettings: GrpcClientSettings): Replica =
    new ReplicaImpl(replicaId, numberOfConsumers, grpcClientSettings, None, None)

}

@ApiMayChange
trait Replica {
  def replicaId: ReplicaId
  def numberOfConsumers: Int
  def grpcClientSettings: GrpcClientSettings
  def additionalQueryRequestMetadata: Option[org.apache.pekko.grpc.scaladsl.Metadata]
  def consumersOnClusterRole: Option[String]

  def withReplicaId(replicaId: ReplicaId): Replica
  def withNumberOfConsumers(numberOfConsumers: Int): Replica
  def withGrpcClientSettings(grpcClientSettings: GrpcClientSettings): Replica

  /**
   * Metadata to include in the requests to the remote Pekko gRPC projection endpoint
   */
  def withAdditionalQueryRequestMetadata(metadata: org.apache.pekko.grpc.scaladsl.Metadata): Replica

  /**
   * Only run consumers for this replica on cluster nodes with this role
   */
  def withConsumersOnClusterRole(clusterRole: String): Replica

}
