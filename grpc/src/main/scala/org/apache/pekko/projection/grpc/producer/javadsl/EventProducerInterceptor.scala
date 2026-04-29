/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.projection.grpc.producer.javadsl

import org.apache.pekko.Done
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.javadsl.Metadata

import java.util.concurrent.CompletionStage

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
