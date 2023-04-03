/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.kafka.internal

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.kafka.KafkaConsumerActor
import pekko.kafka.scaladsl.MetadataClient
import pekko.kafka.ConsumerSettings
import org.apache.kafka.common.TopicPartition

/**
 * INTERNAL API
 */
@InternalApi private[projection] trait MetadataClientAdapter {
  def getBeginningOffsets(assignedTps: Set[TopicPartition]): Future[Map[TopicPartition, Long]]
  def numPartitions(topics: Set[String]): Future[Int]
  def stop(): Unit
}

/**
 * INTERNAL API
 */
@InternalApi private[projection] object MetadataClientAdapterImpl {
  private val KafkaMetadataTimeout = 10.seconds // FIXME get from config

  private val consumerActorNameCounter = new AtomicInteger
  private def nextConsumerActorName(): String =
    s"kafkaSourceProviderConsumer-${consumerActorNameCounter.incrementAndGet()}"
}

/**
 * INTERNAL API
 */
@InternalApi private[projection] class MetadataClientAdapterImpl(
    system: ActorSystem[_],
    settings: ConsumerSettings[_, _])
    extends MetadataClientAdapter {
  import MetadataClientAdapterImpl._

  private val classic = system.classicSystem.asInstanceOf[ExtendedActorSystem]
  implicit val ec: ExecutionContext = classic.dispatcher

  private lazy val consumerActor = classic.systemActorOf(KafkaConsumerActor.props(settings), nextConsumerActorName())
  private lazy val metadataClient = MetadataClient.create(consumerActor, KafkaMetadataTimeout)

  def getBeginningOffsets(assignedTps: Set[TopicPartition]): Future[Map[TopicPartition, Long]] =
    metadataClient.getBeginningOffsets(assignedTps)

  def numPartitions(topics: Set[String]): Future[Int] =
    Future.sequence(topics.map(metadataClient.getPartitionsFor)).map(_.map(_.length).sum)

  def stop(): Unit = {
    metadataClient.close()
    classic.stop(consumerActor)
  }
}
