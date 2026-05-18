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

package org.apache.pekko.projection.grpc.replication.javadsl

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.annotation.ApiMayChange
import pekko.annotation.DoNotInherit
import pekko.cluster.sharding.typed.ReplicatedEntity
import pekko.cluster.sharding.typed.javadsl.Entity
import pekko.cluster.sharding.typed.javadsl.EntityContext
import pekko.cluster.sharding.typed.javadsl.EntityRef
import pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.model.HttpResponse
import pekko.japi.function.{ Function => JFunction }
import pekko.persistence.typed.ReplicationId
import pekko.persistence.typed.internal.ReplicationContextImpl
import pekko.persistence.typed.javadsl.ReplicationContext
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.projection.grpc.producer.javadsl.EventProducer
import pekko.projection.grpc.producer.javadsl.EventProducerSource
import pekko.projection.grpc.replication.internal.ReplicationImpl
import java.util.concurrent.CompletionStage
import java.util.function.Predicate

import pekko.persistence.query.typed.EventEnvelope

/**
 * Created using [[Replication.grpcReplication]], which starts sharding with the entity and
 * replication stream consumers but not the replication endpoint needed to publish events to other replication places.
 *
 * @tparam Command The type of commands the Replicated Event Sourced Entity accepts
 *
 * Not for user extension
 */
@ApiMayChange
@DoNotInherit
trait Replication[Command] {

  /**
   * If combining multiple entity types replicated, or combining with direct usage of
   * Pekko Projection gRPC you will have to use the EventProducerService of each of them
   * in a set passed to EventProducer.grpcServiceHandler to create a single gRPC endpoint
   */
  def eventProducerService: EventProducerSource

  /**
   * If only replicating one Replicated Event Sourced Entity and not using
   * Pekko Projection gRPC this endpoint factory can be used to get a partial function
   * that can be served/bound with an Akka HTTP/2 server
   */
  def createSingleServiceHandler(): JFunction[HttpRequest, CompletionStage[HttpResponse]]

  /**
   * Entity type key for looking up the entities
   */
  def entityTypeKey: EntityTypeKey[Command]

  /**
   * Shortcut for creating EntityRefs for the sharded Replicated Event Sourced entities for
   * sending commands.
   */
  def entityRefFactory: String => EntityRef[Command]
}

@ApiMayChange
object Replication {

  /**
   * Called to bootstrap the entity on each cluster node in each of the replicas.
   *
   * Important: Note that this does not publish the endpoint, additional steps are needed!
   */
  def grpcReplication[Command, Event, State](
      settings: ReplicationSettings[Command],
      replicatedBehaviorFactory: JFunction[ReplicatedBehaviors[Command, Event, State], Behavior[Command]],
      system: ActorSystem[_]): Replication[Command] = {
    val trueProducerFilter = new Predicate[EventEnvelope[Event]] {
      override def test(env: EventEnvelope[Event]): Boolean = true
    }
    grpcReplication[Command, Event, State](settings, trueProducerFilter, replicatedBehaviorFactory, system)
  }

  /**
   * Called to bootstrap the entity on each cluster node in each of the replicas.
   *
   * Important: Note that this does not publish the endpoint, additional steps are needed!
   */
  def grpcReplication[Command, Event, State](
      settings: ReplicationSettings[Command],
      producerFilter: Predicate[EventEnvelope[Event]],
      replicatedBehaviorFactory: JFunction[ReplicatedBehaviors[Command, Event, State], Behavior[Command]],
      system: ActorSystem[_]): Replication[Command] = {

    val scalaReplicationSettings = settings.toScala

    val replicatedEntity =
      ReplicatedEntity[Command](
        settings.selfReplicaId,
        settings.configureEntity
          .apply(
            Entity.of(
              settings.entityTypeKey,
              { (entityContext: EntityContext[Command]) =>
                val replicationId =
                  ReplicationId(entityContext.getEntityTypeKey.name, entityContext.getEntityId, settings.selfReplicaId)
                replicatedBehaviorFactory.apply(factory =>
                  ReplicatedEventSourcing.externalReplication(
                    replicationId,
                    scalaReplicationSettings.otherReplicas.map(_.replicaId) + settings.selfReplicaId)(
                    replicationContext =>
                      factory
                        .apply(replicationContext.asInstanceOf[ReplicationContext])
                        .createEventSourcedBehavior()
                        // MEH
                        .withReplication(replicationContext.asInstanceOf[ReplicationContextImpl])))
              }))
          .toScala)

    val scalaProducerFilter: EventEnvelope[Event] => Boolean = producerFilter.test

    val scalaRESOG =
      ReplicationImpl.grpcReplication[Command, Event, State](
        scalaReplicationSettings,
        scalaProducerFilter,
        replicatedEntity)(system)
    val jEventProducerSource = new EventProducerSource(
      scalaRESOG.eventProducerService.entityType,
      scalaRESOG.eventProducerService.streamId,
      scalaRESOG.eventProducerService.transformation.toJava,
      scalaRESOG.eventProducerService.settings)

    new Replication[Command] {
      override def eventProducerService: EventProducerSource = jEventProducerSource

      override def createSingleServiceHandler(): JFunction[HttpRequest, CompletionStage[HttpResponse]] =
        EventProducer.grpcServiceHandler(system, jEventProducerSource)

      override def entityTypeKey: EntityTypeKey[Command] =
        scalaRESOG.entityTypeKey.asJava

      override def entityRefFactory: String => EntityRef[Command] =
        (entityId: String) => scalaRESOG.entityRefFactory.apply(entityId).asJava

      override def toString: String = scalaRESOG.toString
    }
  }

}
