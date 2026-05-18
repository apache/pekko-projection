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

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.cluster.ClusterActorRefProvider
import org.apache.pekko.cluster.sharding.typed.ReplicatedEntity
import org.apache.pekko.cluster.sharding.typed.ShardedDaemonProcessSettings
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityRef
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.persistence.Persistence
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.persistence.typed.PublishedEvent
import org.apache.pekko.persistence.typed.ReplicationId
import org.apache.pekko.persistence.typed.internal.PublishedEventImpl
import org.apache.pekko.persistence.typed.internal.ReplicatedEventMetadata
import org.apache.pekko.persistence.typed.internal.ReplicatedPublishedEventMetaData
import org.apache.pekko.projection.ProjectionBehavior
import org.apache.pekko.projection.ProjectionContext
import org.apache.pekko.projection.ProjectionId
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.grpc.consumer.GrpcQuerySettings
import org.apache.pekko.projection.grpc.consumer.scaladsl.GrpcReadJournal
import org.apache.pekko.projection.grpc.producer.scaladsl.EventProducer
import org.apache.pekko.projection.grpc.producer.scaladsl.EventProducer.EventProducerSource
import org.apache.pekko.projection.grpc.producer.scaladsl.EventProducer.Transformation
import org.apache.pekko.projection.grpc.replication.scaladsl.Replica
import org.apache.pekko.projection.grpc.replication.scaladsl.Replication
import org.apache.pekko.projection.grpc.replication.scaladsl.ReplicationSettings
import org.apache.pekko.stream.scaladsl.FlowWithContext
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ReplicationImpl[Command] private (
    val eventProducerService: EventProducerSource,
    val createSingleServiceHandler: () => PartialFunction[HttpRequest, Future[HttpResponse]],
    val entityTypeKey: EntityTypeKey[Command],
    val entityRefFactory: String => EntityRef[Command])
    extends Replication[Command] {
  override def toString: String = s"Replication(${eventProducerService.entityType}, ${eventProducerService.streamId})"
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ReplicationImpl {

  private val log = LoggerFactory.getLogger(classOf[ReplicationImpl[_]])

  private val filteredEvent = Future.successful(None)

  /**
   * Called to bootstrap the entity on each cluster node in each of the replicas.
   *
   * Important: Note that this does not publish the endpoint, additional steps are needed!
   */
  def grpcReplication[Command, Event, State](
      settings: ReplicationSettings[Command],
      producerFilter: EventEnvelope[Event] => Boolean,
      replicatedEntity: ReplicatedEntity[Command])(implicit system: ActorSystem[_]): ReplicationImpl[Command] = {
    require(
      system.classicSystem.asInstanceOf[ExtendedActorSystem].provider.isInstanceOf[ClusterActorRefProvider],
      "Replicated Event Sourcing over gRPC only possible together with Akka cluster (org.apache.pekko.actor.provider = cluster)")

    // set up a publisher
    val onlyLocalOriginTransformer = Transformation.empty.registerAsyncEnvelopeOrElseMapper(envelope =>
      envelope.eventMetadata match {
        case Some(meta: ReplicatedEventMetadata) =>
          if (meta.originReplica == settings.selfReplicaId) Future.successful(envelope.eventOption)
          else filteredEvent // Optimization: was replicated to this DC, don't pass the payload across the wire
        case _ =>
          throw new IllegalArgumentException(
            s"Got an event without replication metadata, not supported (pid: ${envelope.persistenceId}, seq_nr: ${envelope.sequenceNr})")
      })
    val eps = EventProducerSource(
      settings.entityTypeKey.name,
      settings.streamId,
      onlyLocalOriginTransformer,
      settings.eventProducerSettings,
      producerFilter.asInstanceOf[EventEnvelope[Any] => Boolean])

    val sharding = ClusterSharding(system)
    sharding.init(replicatedEntity.entity)

    // sharded daemon process for consuming event stream from the other dc:s
    val entityRefFactory: String => EntityRef[Command] = sharding.entityRefFor(replicatedEntity.entity.typeKey, _)
    settings.otherReplicas.foreach(startConsumer(_, settings, entityRefFactory))

    new ReplicationImpl[Command](
      eventProducerService = eps,
      createSingleServiceHandler = () => EventProducer.grpcServiceHandler(Set(eps), settings.eventProducerInterceptor),
      entityTypeKey = replicatedEntity.entity.typeKey,
      entityRefFactory = entityRefFactory)
  }

  private def startConsumer[C](
      remoteReplica: Replica,
      settings: ReplicationSettings[C],
      entityRefFactory: String => EntityRef[C])(implicit system: ActorSystem[_]): Unit = {
    implicit val timeout: Timeout = settings.entityEventReplicationTimeout
    implicit val ec: ExecutionContext = system.executionContext

    val projectionName =
      s"RES_${settings.entityTypeKey.name}_${settings.selfReplicaId.id}_${remoteReplica.replicaId.id}"
    require(
      projectionName.size < 255,
      s"The generated projection name for replica [${remoteReplica.replicaId.id}]: '$projectionName' is too long to fit " +
      "in the database column, must be at most 255 characters. See if you can shorten replica or entity type names.")
    val sliceRanges = Persistence(system).sliceRanges(remoteReplica.numberOfConsumers)

    val grpcQuerySettings = {
      val s = GrpcQuerySettings(settings.streamId)
      remoteReplica.additionalQueryRequestMetadata.fold(s)(s.withAdditionalRequestMetadata)
    }
    val eventsBySlicesQuery = GrpcReadJournal(grpcQuerySettings, remoteReplica.grpcClientSettings, Nil)
    log.infoN(
      "Starting {} projection streams{} consuming events for Replicated Entity [{}] from [{}] (at {}:{})",
      remoteReplica.numberOfConsumers,
      remoteReplica.consumersOnClusterRole.fold("")(role => s" on nodes with cluster role $role"),
      settings.entityTypeKey.name,
      remoteReplica.replicaId.id,
      remoteReplica.grpcClientSettings.serviceName,
      remoteReplica.grpcClientSettings.defaultPort)

    val shardedDaemonProcessSettings = {
      import scala.concurrent.duration._
      val default = ShardedDaemonProcessSettings(system)
      val shardingSettings = default.shardingSettings.getOrElse(ClusterShardingSettings(system))
      // shorter handoff timeout because in some restart backoff it may take a while for the projections to
      // terminate and we don't want to delay sharding rebalance for too long. 10 seconds actually
      // means that it will wait 5 seconds before stopping them hard (5 seconds is reduced in sharding).
      val handOffTimeout = shardingSettings.tuningParameters.handOffTimeout.min(10.seconds)
      val defaultWithShardingSettings = default.withShardingSettings(
        shardingSettings.withTuningParameters(shardingSettings.tuningParameters.withHandOffTimeout(handOffTimeout)))
      remoteReplica.consumersOnClusterRole match {
        case None       => defaultWithShardingSettings
        case Some(role) => defaultWithShardingSettings.withRole(role)
      }
    }
    ShardedDaemonProcess(system).init(projectionName, remoteReplica.numberOfConsumers, { idx =>
      val sliceRange = sliceRanges(idx)
      val projectionKey = s"${sliceRange.min}-${sliceRange.max}"
      val projectionId = ProjectionId(projectionName, projectionKey)

      val replicationFlow: FlowWithContext[EventEnvelope[AnyRef], ProjectionContext, Done, ProjectionContext, NotUsed] =
        FlowWithContext[EventEnvelope[AnyRef], ProjectionContext]
          .via(new ParallelUpdatesFlow[AnyRef](settings.parallelUpdates)({
            envelope =>

              if (!envelope.filtered) {
                envelope.eventMetadata match {
                  case Some(replicatedEventMetadata: ReplicatedEventMetadata) =>
                    // skipping events originating from other replicas is handled by filtering but for good measure
                    require(replicatedEventMetadata.originReplica == remoteReplica.replicaId)

                    val replicationId = ReplicationId.fromString(envelope.persistenceId)
                    val destinationReplicaId = replicationId.withReplica(settings.selfReplicaId)
                    val entityRef =
                      entityRefFactory(destinationReplicaId.entityId).asInstanceOf[EntityRef[PublishedEvent]]
                    if (log.isTraceEnabled) {
                      log.traceN(
                        "[{}] forwarding event originating on dc [{}] to [{}] (origin seq_nr [{}]): [{}]",
                        projectionKey,
                        replicatedEventMetadata.originReplica,
                        destinationReplicaId.persistenceId.id,
                        envelope.sequenceNr,
                        replicatedEventMetadata.version)
                    }
                    val askResult = entityRef.ask[Done](replyTo =>
                      PublishedEventImpl(
                        replicationId.persistenceId,
                        replicatedEventMetadata.originSequenceNr,
                        envelope.event,
                        envelope.timestamp,
                        Some(new ReplicatedPublishedEventMetaData(
                          replicatedEventMetadata.originReplica,
                          replicatedEventMetadata.version)),
                        Some(replyTo)))
                    askResult.failed.foreach(error =>
                      log.warn(
                        s"Failing replication stream [$projectionName/$projectionKey] from [${remoteReplica.replicaId.id}], event pid [${envelope.persistenceId}], seq_nr [${envelope.sequenceNr}]",
                        error))
                    askResult

                  case unexpected =>
                    throw new IllegalArgumentException(
                      s"Got unexpected type of event envelope metadata: ${unexpected.getClass} (pid [${envelope.persistenceId}], seq_nr [${envelope.sequenceNr}]" +
                      ", is the remote entity really a Replicated Event Sourced Entity?")
                }
              } else {
                // Events not originating on sending side already are filtered/have no payload and end up here
                if (log.isTraceEnabled)
                  log.traceN(
                    "[{}] ignoring filtered event from replica [{}] (pid [{}], seq_nr [{}])",
                    projectionKey,
                    remoteReplica.replicaId,
                    envelope.persistenceId,
                    envelope.sequenceNr)
                Future.successful(Done)
              }
          }))
          .map(_ => Done)

      val sourceProvider = EventSourcedProvider.eventsBySlices[AnyRef](
        system,
        eventsBySlicesQuery,
        eventsBySlicesQuery.streamId,
        sliceRange.min,
        sliceRange.max)
      ProjectionBehavior(settings.projectionProvider(projectionId, sourceProvider, replicationFlow, system))
    }, shardedDaemonProcessSettings, Some(ProjectionBehavior.Stop))
  }

}
