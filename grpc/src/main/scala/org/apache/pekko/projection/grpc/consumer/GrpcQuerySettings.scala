/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.projection.grpc.consumer

import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.scaladsl.Metadata
import org.apache.pekko.grpc.scaladsl.MetadataBuilder
import org.apache.pekko.projection.grpc.consumer.scaladsl.GrpcReadJournal
import com.typesafe.config.Config

@ApiMayChange
object GrpcQuerySettings {

  /**
   * Scala API: From `Config` `pekko.projection.grpc.consumer` configuration section.
   */
  def apply(system: ClassicActorSystemProvider): GrpcQuerySettings =
    apply(system.classicSystem.settings.config.getConfig(GrpcReadJournal.Identifier))

  /**
   * Scala API: From `Config` corresponding to `pekko.projection.grpc.consumer` configuration section.
   */
  def apply(config: Config): GrpcQuerySettings = {
    val streamId = config.getString("stream-id")
    require(
      streamId != "",
      "Configuration property [stream-id] must be an id exposed by the producing side but was undefined on the consuming side.")

    val additionalHeaders: Option[Metadata] = {
      import scala.jdk.CollectionConverters._
      val map = config.getConfig("additional-request-headers").root.unwrapped.asScala.toMap.map {
        case (k, v) => k -> v.toString
      }
      if (map.isEmpty) None
      else
        Some(
          map
            .foldLeft(new MetadataBuilder()) {
              case (builder, (key, value)) =>
                builder.addText(key, value)
            }
            .build())
    }

    new GrpcQuerySettings(streamId, additionalHeaders)
  }

  /**
   * Java API: From `Config` `pekko.projection.grpc.consumer` configuration section.
   */
  def create(system: ClassicActorSystemProvider): GrpcQuerySettings =
    apply(system)

  /**
   * Java API: From `Config` corresponding to `pekko.projection.grpc.consumer` configuration section.
   */
  def create(config: Config): GrpcQuerySettings =
    apply(config)

  /**
   * Scala API: Programmatic construction of GrpcQuerySettings
   *
   * @param streamId The stream id to consume. It is exposed by the producing side.
   */
  def apply(streamId: String): GrpcQuerySettings = {
    new GrpcQuerySettings(streamId, additionalRequestMetadata = None)
  }

  /**
   * Java API: Programmatic construction of GrpcQuerySettings
   *
   * @param streamId The stream id to consume. It is exposed by the producing side.
   */
  def create(streamId: String): GrpcQuerySettings = {
    new GrpcQuerySettings(streamId, additionalRequestMetadata = None)
  }
}

@ApiMayChange
final class GrpcQuerySettings private (val streamId: String, val additionalRequestMetadata: Option[Metadata]) {
  require(
    streamId != "",
    "streamId must be an id exposed by the producing side but was undefined on the consuming side.")

  /**
   * Additional request metadata, for authentication/authorization of the request on the remote side.
   */
  def withAdditionalRequestMetadata(metadata: Metadata): GrpcQuerySettings =
    new GrpcQuerySettings(streamId, Option(metadata))

}
