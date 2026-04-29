/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.projection.grpc.producer.javadsl;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.grpc.producer.EventProducerSettings;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class ProducerCompileTest {

  interface SomeEvent {}

  ActorSystem<?> system = ActorSystem.create(Behaviors.empty(), "test");

  public void compileTest() {
    EventProducerSettings settings = EventProducerSettings.create(system);

    Transformation transformation =
        Transformation.identity()
            .registerMapper(SomeEvent.class, e -> Optional.of(e))
            .registerAsyncMapper(
                SomeEvent.class, e -> java.util.concurrent.CompletableFuture.completedFuture(Optional.of(e)));

    EventProducerSource source =
        new EventProducerSource("entityType", "streamId", transformation, settings);

    Set<EventProducerSource> sources = Collections.singleton(source);

    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        EventProducer.grpcServiceHandler(system, sources);

    Function<HttpRequest, CompletionStage<HttpResponse>> handlerWithInterceptor =
        EventProducer.grpcServiceHandler(
            system, sources, Optional.of((streamId, meta) -> java.util.concurrent.CompletableFuture.completedFuture(org.apache.pekko.Done.getInstance())));
  }
}
