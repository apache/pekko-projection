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

package org.apache.pekko.projection.grpc.consumer.javadsl;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import org.apache.pekko.grpc.GrpcClientSettings;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.Persistence;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.grpc.consumer.ConsumerFilter;
import org.apache.pekko.projection.grpc.consumer.GrpcQuerySettings;
import org.apache.pekko.projection.javadsl.Handler;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcProjection;
import com.example.shoppingcart.ShoppingcartApiProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.pekko.Done.done;

public class ConsumerCompileTest {
  static class EventHandler extends Handler<EventEnvelope<String>> {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public CompletionStage<Done> process(EventEnvelope<String> envelope) {
      log.info("Consumed event: {}", envelope);
      return CompletableFuture.completedFuture(done());
    }
  }

  public static void init(ActorSystem<?> system) {
    int numberOfProjectionInstances = 1;
    String projectionName = "cart-events";
    List<Pair<Integer, Integer>> sliceRanges =
        Persistence.get(system).getSliceRanges(numberOfProjectionInstances);
    String streamId = "ShoppingCart";

    GrpcReadJournal eventsBySlicesQuery =
        GrpcReadJournal.create(
            system,
            GrpcQuerySettings.create(streamId),
            GrpcClientSettings.fromConfig(
                system.settings().config().getConfig("pekko.projection.grpc.consumer.client"),
                system),
            List.of(ShoppingcartApiProto.javaDescriptor()));

    ShardedDaemonProcess.get(system)
        .init(
            ProjectionBehavior.Command.class,
            projectionName,
            numberOfProjectionInstances,
            idx -> {
              Pair<Integer, Integer> sliceRange = sliceRanges.get(idx);
              String projectionKey =
                  eventsBySlicesQuery.streamId()
                      + "-"
                      + sliceRange.first()
                      + "-"
                      + sliceRange.second();
              ProjectionId projectionId = ProjectionId.of(projectionName, projectionKey);

              SourceProvider<Offset, EventEnvelope<String>> sourceProvider =
                  EventSourcedProvider.eventsBySlices(
                      system,
                      eventsBySlicesQuery,
                      eventsBySlicesQuery.streamId(),
                      sliceRange.first(),
                      sliceRange.second());

              return ProjectionBehavior.create(
                  R2dbcProjection.atLeastOnceAsync(
                      projectionId, Optional.empty(), sourceProvider, EventHandler::new, system));
            },
            ProjectionBehavior.stopMessage());
  }

  static void updateConsumerFilter(
      ActorSystem<?> system, Set<String> excludeTags, Set<String> includeTags) {
    String streamId =
        system.settings().config().getString("pekko.projection.grpc.consumer.stream-id");

    List<ConsumerFilter.FilterCriteria> criteria =
        List.of(
            new ConsumerFilter.ExcludeTags(excludeTags),
            new ConsumerFilter.IncludeTags(includeTags));

    ConsumerFilter.get(system).ref().tell(new ConsumerFilter.UpdateFilter(streamId, criteria));
  }
}
