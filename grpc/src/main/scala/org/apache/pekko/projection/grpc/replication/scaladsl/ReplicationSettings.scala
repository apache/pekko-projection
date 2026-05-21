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
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.grpc.GrpcClientSettings
import pekko.persistence.typed.ReplicaId
import pekko.projection.grpc.producer.EventProducerSettings
import pekko.projection.grpc.producer.scaladsl.EventProducerInterceptor
import pekko.projection.grpc.replication.internal.ReplicaImpl
import pekko.projection.grpc.replication.scaladsl
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.reflect.ClassTag

@ApiMayChange
object ReplicationSettings {

  /**
   * Settings for replicating an entity over gRPC
   *
   * Note: The replica ids and the entity type name is used as id in offset tracking, changing those will replay
   * events from the start.
   *
   * @param entityTypeName                A name for the type of replicated entity
   * @param selfReplicaId                 The replica id of this node, must not be present among 'otherReplicas'
   * @param eventProducerSettings         Event producer settings for the event stream published by this replica
   * @param replicas                 One entry for each remote replica to replicate into this replica
   * @param entityEventReplicationTimeout A timeout for the replication event, needs to be large enough for the time
   *                                      of sending a message across sharding and persisting it in the local replica
   *                                      of an entity. Hitting this timeout means the entire replication stream will
   *                                      back off and restart.
   * @param parallelUpdates               Maximum number of parallel updates sent over sharding to the destination entities
   * @param replicationProjectionProvider A factory for the projection used to keep track of offsets when consuming replicated events
   */
  def apply[Command: ClassTag](
      entityTypeName: String,
      selfReplicaId: ReplicaId,
      eventProducerSettings: EventProducerSettings,
      replicas: Set[Replica],
      entityEventReplicationTimeout: FiniteDuration,
      parallelUpdates: Int,
      replicationProjectionProvider: scaladsl.ReplicationProjectionProvider): ReplicationSettings[Command] = {
    val entityTypeKey = EntityTypeKey(entityTypeName)
    apply(
      entityTypeKey,
      selfReplicaId,
      eventProducerSettings,
      replicas,
      entityEventReplicationTimeout,
      parallelUpdates,
      replicationProjectionProvider)
  }

  @InternalApi
  private[pekko] def apply[Command](
      entityTypeKey: EntityTypeKey[Command],
      selfReplicaId: ReplicaId,
      eventProducerSettings: EventProducerSettings,
      otherReplicas: Set[Replica],
      entityEventReplicationTimeout: FiniteDuration,
      parallelUpdates: Int,
      replicationProjectionProvider: scaladsl.ReplicationProjectionProvider): ReplicationSettings[Command] = {
    new ReplicationSettings(
      selfReplicaId = selfReplicaId,
      entityTypeKey = entityTypeKey,
      eventProducerSettings = eventProducerSettings,
      streamId = entityTypeKey.name,
      otherReplicas = otherReplicas.filter(_.replicaId != selfReplicaId),
      entityEventReplicationTimeout = entityEventReplicationTimeout,
      parallelUpdates = parallelUpdates,
      projectionProvider = replicationProjectionProvider,
      None,
      identity)
  }

  /**
   * Create settings from config, the system config is expected to contain a block with the entity type key name.
   * Each replica is further expected to have a top level config entry 'org.apache.pekko.grpc.client.[replica-id]' with Pekko gRPC
   * client config for reaching the replica from the other replicas.
   */
  def apply[Command](entityTypeName: String, replicationProjectionProvider: scaladsl.ReplicationProjectionProvider)(
      implicit system: ActorSystem[_],
      classTag: ClassTag[Command]): ReplicationSettings[Command] = {
    val config = system.settings.config.getConfig(entityTypeName)
    val entityTypeKey = EntityTypeKey(entityTypeName)

    // Note: any changes here needs to be reflected in Java ReplicationSettings config loading
    val selfReplicaId = ReplicaId(config.getString("self-replica-id"))
    val grpcClientFallBack = system.settings.config.getConfig("""pekko.grpc.client."*"""")
    val allReplicas: Set[Replica] = config
      .getConfigList("replicas")
      .asScala
      .toSet
      .map { (config: Config) =>
        val replicaId = config.getString("replica-id")
        val clientConfig =
          config.getConfig("grpc.client").withFallback(grpcClientFallBack)

        val consumersOnRole =
          if (config.hasPath("consumers-on-cluster-role")) Some(config.getString("consumers-on-cluster-role"))
          else None
        new ReplicaImpl(
          ReplicaId(replicaId),
          numberOfConsumers = config.getInt("number-of-consumers"),
          // so org.apache.pekko.grpc.client.[replica-id]
          grpcClientSettings = GrpcClientSettings.fromConfig(clientConfig)(system),
          None,
          consumersOnRole)
      }

    new ReplicationSettings[Command](
      selfReplicaId = selfReplicaId,
      entityTypeKey = entityTypeKey,
      eventProducerSettings = EventProducerSettings(system),
      streamId = entityTypeName,
      otherReplicas = allReplicas.filter(_.replicaId != selfReplicaId),
      entityEventReplicationTimeout = config
        .getDuration("entity-event-replication-timeout")
        .toScala,
      parallelUpdates = config.getInt("parallel-updates"),
      projectionProvider = replicationProjectionProvider,
      None,
      identity)
  }

}

/**
 * Not for user extension. Constructed through companion object factories.
 */
@ApiMayChange
final class ReplicationSettings[Command] private (
    val selfReplicaId: ReplicaId,
    val entityTypeKey: EntityTypeKey[Command],
    val eventProducerSettings: EventProducerSettings,
    val streamId: String,
    val otherReplicas: Set[Replica],
    val entityEventReplicationTimeout: FiniteDuration,
    val parallelUpdates: Int,
    val projectionProvider: ReplicationProjectionProvider,
    val eventProducerInterceptor: Option[EventProducerInterceptor],
    val configureEntity: Entity[Command, ShardingEnvelope[Command]] => Entity[Command, ShardingEnvelope[Command]]) {

  require(
    !otherReplicas.exists(_.replicaId == selfReplicaId),
    s"selfReplicaId [$selfReplicaId] must not be in 'otherReplicas'")
  require(
    (otherReplicas.map(_.replicaId) + selfReplicaId).size == otherReplicas.size + 1,
    s"selfReplicaId and replica ids of the other replicas must be unique, duplicates found: (${otherReplicas.map(
        _.replicaId) + selfReplicaId}")

  def withSelfReplicaId(selfReplicaId: ReplicaId): ReplicationSettings[Command] =
    copy(selfReplicaId = selfReplicaId)

  def withEventProducerSettings(eventProducerSettings: EventProducerSettings): ReplicationSettings[Command] =
    copy(eventProducerSettings = eventProducerSettings)

  def withStreamId(streamId: String): ReplicationSettings[Command] =
    copy(streamId = streamId)

  def withOtherReplicas(replicas: Set[Replica]): ReplicationSettings[Command] =
    copy(otherReplicas = replicas)

  /**
   * Set the timeout for events being completely processed after arriving to a node in the replication stream
   */
  def withEntityEventReplicationTimeout(duration: FiniteDuration): ReplicationSettings[Command] =
    copy(entityEventReplicationTimeout = duration)

  /**
   * Run up to this many parallel updates over sharding. Note however that updates for the same persistence id
   * is always sequential.
   */
  def withParallelUpdates(parallelUpdates: Int): ReplicationSettings[Command] =
    copy(parallelUpdates = parallelUpdates)

  /**
   * Change projection provider
   */
  def withProjectionProvider(projectionProvider: ReplicationProjectionProvider): ReplicationSettings[Command] =
    copy(projectionProvider = projectionProvider)

  /**
   * Add an interceptor to the gRPC event producer for example for authentication of incoming requests
   */
  def withEventProducerInterceptor(interceptor: EventProducerInterceptor): ReplicationSettings[Command] =
    copy(producerInterceptor = Some(interceptor))

  /**
   * Allows for changing the settings of the replicated entity, such as stop message, passivation strategy etc.
   */
  def configureEntity(
      configure: Entity[Command, ShardingEnvelope[Command]] => Entity[Command, ShardingEnvelope[Command]])
      : ReplicationSettings[Command] =
    copy(configureEntity = configure)

  private def copy(
      selfReplicaId: ReplicaId = selfReplicaId,
      entityTypeKey: EntityTypeKey[Command] = entityTypeKey,
      eventProducerSettings: EventProducerSettings = eventProducerSettings,
      streamId: String = streamId,
      otherReplicas: Set[Replica] = otherReplicas,
      entityEventReplicationTimeout: FiniteDuration = entityEventReplicationTimeout,
      parallelUpdates: Int = parallelUpdates,
      projectionProvider: ReplicationProjectionProvider = projectionProvider,
      producerInterceptor: Option[EventProducerInterceptor] = eventProducerInterceptor,
      configureEntity: Entity[Command, ShardingEnvelope[Command]] => Entity[Command, ShardingEnvelope[Command]] =
        configureEntity) =
    new ReplicationSettings[Command](
      selfReplicaId,
      entityTypeKey,
      eventProducerSettings,
      streamId,
      otherReplicas,
      entityEventReplicationTimeout,
      parallelUpdates,
      projectionProvider,
      producerInterceptor,
      configureEntity)

  override def toString = s"ReplicationSettings($selfReplicaId, $entityTypeKey, $streamId, $otherReplicas)"

}
