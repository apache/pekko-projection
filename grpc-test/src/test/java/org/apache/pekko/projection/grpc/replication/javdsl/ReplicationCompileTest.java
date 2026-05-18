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

package org.apache.pekko.projection.grpc.replication.javdsl;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.grpc.javadsl.ServiceHandler;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.projection.ProjectionContext;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.grpc.producer.EventProducerSettings;
import org.apache.pekko.projection.grpc.producer.javadsl.EventProducer;
import org.apache.pekko.projection.grpc.producer.javadsl.EventProducerSource;
import org.apache.pekko.projection.grpc.replication.javadsl.Replica;
import org.apache.pekko.projection.grpc.replication.javadsl.ReplicatedBehaviors;
import org.apache.pekko.projection.grpc.replication.javadsl.Replication;
import org.apache.pekko.projection.grpc.replication.javadsl.ReplicationProjectionProvider;
import org.apache.pekko.projection.grpc.replication.javadsl.ReplicationSettings;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.r2dbc.R2dbcProjectionSettings;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcProjection;
import org.apache.pekko.projection.javadsl.AtLeastOnceFlowProjection;
import org.apache.pekko.stream.javadsl.FlowWithContext;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class ReplicationCompileTest {
  interface MyCommand {}

  static class MyReplicatedBehavior {

    static Behavior<MyCommand> create(
        ReplicatedBehaviors<MyCommand, Void, Void> replicatedBehaviors) {
      return replicatedBehaviors.setup(
          replicationContext -> {
            throw new UnsupportedOperationException("just a dummy factory method");
          });
    }
  }

  public static void start(ActorSystem<?> system) {
    Set<Replica> otherReplicas = new HashSet<>();
    otherReplicas.add(
        Replica.create(
            new ReplicaId("DCB"),
            2,
            GrpcClientSettings.connectToServiceAt("b.example.com", 443, system).withTls(true)));
    otherReplicas.add(
        Replica.create(
            new ReplicaId("DCC"),
            2,
            GrpcClientSettings.connectToServiceAt("c.example.com", 443, system).withTls(true)));

    ReplicationProjectionProvider projectionProvider =
        new ReplicationProjectionProvider() {

          @Override
          public AtLeastOnceFlowProjection<Offset, EventEnvelope<Object>> create(
              ProjectionId projectionId,
              SourceProvider<Offset, EventEnvelope<Object>> sourceProvider,
              FlowWithContext<
                      EventEnvelope<Object>, ProjectionContext, Done, ProjectionContext, NotUsed>
                  replicationFlow,
              ActorSystem<?> system) {
            return R2dbcProjection.atLeastOnceFlow(
                projectionId,
                Optional.<R2dbcProjectionSettings>empty(),
                sourceProvider,
                replicationFlow,
                system);
          }
        };

    // SAM so this is possible
    ReplicationProjectionProvider projectionProvider2 =
        (projectionId, sourceProvider, flow, s) ->
            R2dbcProjection.atLeastOnceFlow(
                projectionId, Optional.empty(), sourceProvider, flow, s);

    ReplicationSettings<MyCommand> settings =
        ReplicationSettings.<MyCommand>create(
                MyCommand.class,
                "my-entity",
                ReplicaId.apply("DCA"),
                EventProducerSettings.apply(system),
                otherReplicas,
                Duration.ofSeconds(10),
                // parallel updates
                8,
                projectionProvider)
            .configureEntity(entity -> entity.withRole("entities"));

    Replication<MyCommand> replication =
        Replication.grpcReplication(settings, MyReplicatedBehavior::create, system);

    // bind a single handler endpoint
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        replication.createSingleServiceHandler();

    @SuppressWarnings("unchecked")
    Function<HttpRequest, CompletionStage<HttpResponse>> service =
        ServiceHandler.concatOrNotFound(handler);

    CompletionStage<ServerBinding> bound =
        Http.get(system).newServerAt("127.0.0.1", 8080).bind(service);
  }

  static class ShoppingCart {
    static Replication<MyCommand> init(ActorSystem<?> system) {
      throw new UnsupportedOperationException("Just a sample");
    }
  }

  public static void multiEventProducers(
      ActorSystem<?> system, ReplicationSettings<MyCommand> settings, String host, int port) {

    Replication<Void> otherReplication = null;

    // #multi-service
    Set<EventProducerSource> allSources = new HashSet<>();

    Replication<MyCommand> replication = ShoppingCart.init(system);
    allSources.add(replication.eventProducerService());

    // add additional EventProducerSource from other entities or
    // Pekko Projection gRPC
    allSources.add(otherReplication.eventProducerService());

    Function<HttpRequest, CompletionStage<HttpResponse>> route =
        EventProducer.grpcServiceHandler(system, allSources);

    @SuppressWarnings("unchecked")
    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        ServiceHandler.concatOrNotFound(route);
    // #multi-service

    CompletionStage<ServerBinding> bound = Http.get(system).newServerAt(host, port).bind(handler);
  }
}
