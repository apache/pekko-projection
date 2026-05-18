/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.producer.javadsl;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.grpc.javadsl.ServiceHandler;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.grpc.producer.EventProducerSettings;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.persistence.query.typed.EventEnvelope;

public class ProducerCompileTest {
  public static void start(ActorSystem<?> system) {
    Transformation asyncTransformation =
        Transformation.empty()
            .registerAsyncMapper(
                Integer.class,
                event ->
                    CompletableFuture.completedFuture(
                        Optional.of(Integer.valueOf(event * 2).toString())))
            .registerAsyncOrElseMapper(
                event -> CompletableFuture.completedFuture(Optional.of(event.toString())));
    Transformation transformation =
        Transformation.empty()
            .registerMapper(
                Integer.class, event -> Optional.of(Integer.valueOf(event * 2).toString()))
            .registerOrElseMapper(event -> Optional.of(event.toString()));
    Transformation lowLevel = Transformation.empty().registerAsyncEnvelopeMapper(
        Integer.class, envelope -> CompletableFuture.completedFuture(envelope.getOptionalEvent())
    ).registerAsyncEnvelopeOrElseMapper(envelope -> CompletableFuture.completedFuture(Optional.empty()));

    EventProducerSource source =
        new EventProducerSource(
            "ShoppingCart", "cart", transformation, EventProducerSettings.apply(system))
            .withProducerFilter((EventEnvelope<Integer> env) -> env.event().doubleValue() > 0.0);

    Function<HttpRequest, CompletionStage<HttpResponse>> eventProducerService =
        EventProducer.grpcServiceHandler(system, source);
    Function<HttpRequest, CompletionStage<HttpResponse>> eventProducerServiceWithMultiple =
        EventProducer.grpcServiceHandler(system, Collections.singleton(source));

    @SuppressWarnings("unchecked")
    Function<HttpRequest, CompletionStage<HttpResponse>> service =
        ServiceHandler.concatOrNotFound(eventProducerService);

    CompletionStage<ServerBinding> bound =
        Http.get(system).newServerAt("127.0.0.1", 8080).bind(service);
  }
}
