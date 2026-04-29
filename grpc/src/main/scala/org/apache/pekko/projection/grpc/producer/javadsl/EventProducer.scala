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

package org.apache.pekko.projection.grpc.producer.javadsl

import java.util.Collections
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.annotation.ApiMayChange
import org.apache.pekko.grpc.internal.JavaMetadataImpl
import org.apache.pekko.grpc.scaladsl.{ Metadata => ScalaMetadata }
import org.apache.pekko.http.javadsl.model.HttpRequest
import org.apache.pekko.http.javadsl.model.HttpResponse
import org.apache.pekko.japi.function.{ Function => JapiFunction }
import org.apache.pekko.projection.grpc.internal.EventProducerServiceImpl
import org.apache.pekko.projection.grpc.internal.proto.EventProducerServicePowerApiHandler
import scala.jdk.CollectionConverters._

/**
 * The event producer implementation that can be included a gRPC route in a Pekko HTTP server.
 */
@ApiMayChange
object EventProducer {

  /**
   * The gRPC route that can be included in a Pekko HTTP server.
   *
   * @param source The source that should be available from this event producer
   */
  def grpcServiceHandler(
      system: ActorSystem[_],
      source: EventProducerSource): JapiFunction[HttpRequest, CompletionStage[HttpResponse]] =
    grpcServiceHandler(system, Collections.singleton(source))

  /**
   * The gRPC route that can be included in a Pekko HTTP server.
   *
   * @param sources All sources that should be available from this event producer
   */
  def grpcServiceHandler(
      system: ActorSystem[_],
      sources: java.util.Set[EventProducerSource]): JapiFunction[HttpRequest, CompletionStage[HttpResponse]] =
    grpcServiceHandler(system, sources, Optional.empty())

  /**
   * The gRPC route that can be included in a Pekko HTTP server.
   *
   * @param sources All sources that should be available from this event producer
   * @param interceptor An optional request interceptor applied to each request to the service
   */
  def grpcServiceHandler(
      system: ActorSystem[_],
      sources: java.util.Set[EventProducerSource],
      interceptor: Optional[EventProducerInterceptor]): JapiFunction[HttpRequest, CompletionStage[HttpResponse]] = {
    val scalaProducerSources = sources.asScala.map(_.asScala).toSet
    val eventsBySlicesQueriesPerStreamId =
      org.apache.pekko.projection.grpc.producer.scaladsl.EventProducer
        .eventsBySlicesQueriesForStreamIds(scalaProducerSources, system)

    val eventProducerService = new EventProducerServiceImpl(
      system,
      eventsBySlicesQueriesPerStreamId,
      scalaProducerSources,
      interceptor.toScala.map(new InterceptorAdapter(_)))

    val handler = EventProducerServicePowerApiHandler(eventProducerService)(system)
    new JapiFunction[HttpRequest, CompletionStage[HttpResponse]] {
      override def apply(request: HttpRequest): CompletionStage[HttpResponse] =
        handler(request.asInstanceOf[org.apache.pekko.http.scaladsl.model.HttpRequest])
          .map(_.asInstanceOf[HttpResponse])(ExecutionContext.parasitic)
          .asJava
    }
  }

  private final class InterceptorAdapter(interceptor: EventProducerInterceptor)
      extends org.apache.pekko.projection.grpc.producer.scaladsl.EventProducerInterceptor {
    def intercept(streamId: String, requestMetadata: ScalaMetadata): Future[Done] =
      interceptor
        .intercept(
          streamId,
          new JavaMetadataImpl(requestMetadata))
        .asScala
  }

}
