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

package org.apache.pekko.projection.grpc.producer.javadsl

import pekko.Done
import pekko.annotation.ApiMayChange
import pekko.grpc.internal.JavaMetadataImpl
import pekko.grpc.javadsl.Metadata
import pekko.grpc.scaladsl

import java.util.concurrent.CompletionStage
import scala.jdk.FutureConverters._
import scala.concurrent.Future

/**
 * Interceptor allowing for example authentication/authorization of incoming requests to consume a specific stream.
 */
@ApiMayChange
@FunctionalInterface
trait EventProducerInterceptor {

  /**
   * Let's requests through if method returns, can fail request by throwing a [[org.apache.pekko.grpc.GrpcServiceException]]
   */
  def intercept(streamId: String, requestMetadata: Metadata): CompletionStage[Done]

}

/**
 * INTERNAL API
 */
private[pekko] final class EventProducerInterceptorAdapter(interceptor: EventProducerInterceptor)
    extends org.apache.pekko.projection.grpc.producer.scaladsl.EventProducerInterceptor {
  override def intercept(streamId: String, requestMetadata: scaladsl.Metadata): Future[Done] =
    interceptor
      .intercept(
        streamId,
        // FIXME: Akka gRPC internal class, add public API for Scala to Java metadata there
        new JavaMetadataImpl(requestMetadata))
      .asScala
}
