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

package org.apache.pekko.projection.grpc.consumer.javadsl

import java.time.Instant
import java.util
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.grpc.GrpcClientSettings
import pekko.japi.Pair
import pekko.persistence.query.Offset
import pekko.persistence.query.javadsl.ReadJournal
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.query.typed.javadsl.EventTimestampQuery
import pekko.persistence.query.typed.javadsl.EventsBySliceQuery
import pekko.persistence.query.typed.javadsl.LoadEventQuery
import pekko.projection.grpc.consumer.GrpcQuerySettings
import pekko.projection.grpc.consumer.scaladsl
import pekko.projection.grpc.internal.ProtoAnySerialization
import pekko.stream.javadsl.Source
import com.google.protobuf.Descriptors

@ApiMayChange
object GrpcReadJournal {
  val Identifier: String = scaladsl.GrpcReadJournal.Identifier

  /**
   * Construct a gRPC read journal from configuration `pekko.projection.grpc.consumer`. The `stream-id` must
   * be defined in the configuration.
   */
  def create(
      system: ClassicActorSystemProvider,
      protobufDescriptors: java.util.List[Descriptors.FileDescriptor]): GrpcReadJournal =
    create(
      system,
      GrpcQuerySettings(system),
      GrpcClientSettings.fromConfig(system.classicSystem.settings.config.getConfig(Identifier + ".client"))(system),
      protobufDescriptors)

  /**
   * Construct a gRPC read journal for the given stream-id and explicit `GrpcClientSettings` to control
   * how to reach the Pekko Projection gRPC producer service (host, port etc).
   */
  def create(
      system: ClassicActorSystemProvider,
      settings: GrpcQuerySettings,
      clientSettings: GrpcClientSettings,
      protobufDescriptors: java.util.List[Descriptors.FileDescriptor]): GrpcReadJournal = {
    import scala.jdk.CollectionConverters._
    new GrpcReadJournal(scaladsl
      .GrpcReadJournal(settings, clientSettings, protobufDescriptors.asScala.toList, ProtoAnySerialization.Prefer.Java)(
        system))
  }

}

@ApiMayChange
class GrpcReadJournal(delegate: scaladsl.GrpcReadJournal)
    extends ReadJournal
    with EventsBySliceQuery
    with EventTimestampQuery
    with LoadEventQuery {

  /**
   * The identifier of the stream to consume, which is exposed by the producing/publishing side.
   * It is defined in the [[GrpcQuerySettings]].
   */
  def streamId(): String =
    delegate.streamId

  override def eventsBySlices[Event](
      entityType: String,
      minSlice: Int,
      maxSlice: Int,
      offset: Offset): Source[EventEnvelope[Event], NotUsed] =
    delegate.eventsBySlices(entityType, minSlice, maxSlice, offset).asJava

  override def sliceForPersistenceId(persistenceId: String): Int =
    delegate.sliceForPersistenceId(persistenceId)

  override def sliceRanges(numberOfRanges: Int): util.List[Pair[Integer, Integer]] = {
    import scala.jdk.CollectionConverters._
    delegate
      .sliceRanges(numberOfRanges)
      .map(range => Pair(Integer.valueOf(range.min), Integer.valueOf(range.max)))
      .asJava
  }

  override def timestampOf(persistenceId: String, sequenceNr: Long): CompletionStage[Optional[Instant]] =
    delegate
      .timestampOf(persistenceId, sequenceNr)
      .map(_.toJava)(ExecutionContext.parasitic)
      .asJava

  override def loadEnvelope[Event](persistenceId: String, sequenceNr: Long): CompletionStage[EventEnvelope[Event]] =
    delegate.loadEnvelope[Event](persistenceId, sequenceNr).asJava

  /**
   * Close the gRPC client. It will be automatically closed when the `ActorSystem` is terminated,
   * so invoking this is only needed when there is a need to close the resource before that.
   * After closing the `GrpcReadJournal` instance cannot be used again.
   */
  def close(): CompletionStage[Done] =
    delegate.close().asJava
}
