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

package jdocs.home.projection;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;

import docs.home.CborSerializable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// #handler
// #grouped-handler
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcHandler;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession;
import io.r2dbc.spi.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// #grouped-handler
// #handler

// #initProjections
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.Projection;
// #initProjections

// #exactlyOnce
// #atLeastOnce
// #grouped
// #initProjections
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.r2dbc.R2dbcProjectionSettings;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcProjection;

// #initProjections
// #grouped
// #atLeastOnce
// #exactlyOnce

@SuppressWarnings({"unused", "InnerClassMayBeStatic"})
class R2dbcProjectionDocExample {

  static class ShoppingCart {
    public static EntityTypeKey<Command> ENTITY_TYPE_KEY =
        EntityTypeKey.create(Command.class, "ShoppingCart");

    interface Command extends CborSerializable {}

    interface Event {
      String getCartId();
    }

    public static class CheckedOut implements Event {

      public final String cartId;
      public final Instant eventTime;

      public CheckedOut(String cartId, Instant eventTime) {
        this.cartId = cartId;
        this.eventTime = eventTime;
      }

      public String getCartId() {
        return cartId;
      }

      @Override
      public String toString() {
        return "CheckedOut(" + cartId + "," + eventTime + ")";
      }
    }
  }

  // #handler
  public class ShoppingCartHandler extends R2dbcHandler<EventEnvelope<ShoppingCart.Event>> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public CompletionStage<Done> process(
        R2dbcSession session, EventEnvelope<ShoppingCart.Event> envelope) {
      ShoppingCart.Event event = envelope.event();
      if (event instanceof ShoppingCart.CheckedOut) {
        ShoppingCart.CheckedOut checkedOut = (ShoppingCart.CheckedOut) event;
        logger.info(
            "Shopping cart {} was checked out at {}", checkedOut.cartId, checkedOut.eventTime);

        Statement stmt =
            session
                .createStatement("INSERT into order (id, time) VALUES ($1, $2)")
                .bind(0, checkedOut.cartId)
                .bind(1, checkedOut.eventTime);
        return session.updateOne(stmt).thenApply(rowsUpdated -> Done.getInstance());

      } else {
        logger.debug("Shopping cart {} changed by {}", event.getCartId(), event);
        return CompletableFuture.completedFuture(Done.getInstance());
      }
    }
  }

  // #handler

  // #grouped-handler
  public class GroupedShoppingCartHandler
      extends R2dbcHandler<List<EventEnvelope<ShoppingCart.Event>>> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public CompletionStage<Done> process(
        R2dbcSession session, List<EventEnvelope<ShoppingCart.Event>> envelopes) {
      List<Statement> stmts = new ArrayList<>();
      for (EventEnvelope<ShoppingCart.Event> envelope : envelopes) {
        ShoppingCart.Event event = envelope.event();
        if (event instanceof ShoppingCart.CheckedOut) {
          ShoppingCart.CheckedOut checkedOut = (ShoppingCart.CheckedOut) event;
          logger.info(
              "Shopping cart {} was checked out at {}", checkedOut.cartId, checkedOut.eventTime);

          Statement stmt =
              session
                  .createStatement("INSERT into order (id, time) VALUES ($1, $2)")
                  .bind(0, checkedOut.cartId)
                  .bind(1, checkedOut.eventTime);
          stmts.add(stmt);
        } else {
          logger.debug("Shopping cart {} changed by {}", event.getCartId(), event);
        }
      }

      return session.update(stmts).thenApply(rowsUpdated -> Done.getInstance());
    }
  }

  // #grouped-handler

  ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

  // #initProjections
  void initProjections() {
    // Split the slices into 4 ranges
    int numberOfSliceRanges = 4;
    List<Pair<Integer, Integer>> sliceRanges =
        EventSourcedProvider.sliceRanges(
            system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);

    ShardedDaemonProcess.get(system)
        .init(
            ProjectionBehavior.Command.class,
            "ShoppingCartProjection",
            sliceRanges.size(),
            i -> ProjectionBehavior.create(createProjection(sliceRanges.get(i))),
            ProjectionBehavior.stopMessage());
  }

  Projection<EventEnvelope<ShoppingCart.Event>> createProjection(
      Pair<Integer, Integer> sliceRange) {
    int minSlice = sliceRange.first();
    int maxSlice = sliceRange.second();

    String entityType = ShoppingCart.ENTITY_TYPE_KEY.name();

    SourceProvider<Offset, EventEnvelope<ShoppingCart.Event>> sourceProvider =
        EventSourcedProvider.eventsBySlices(
            system, R2dbcReadJournal.Identifier(), entityType, minSlice, maxSlice);

    ProjectionId projectionId =
        ProjectionId.of("ShoppingCarts", "carts-" + minSlice + "-" + maxSlice);
    Optional<R2dbcProjectionSettings> settings = Optional.empty();

    return R2dbcProjection.exactlyOnce(
        projectionId, settings, sourceProvider, ShoppingCartHandler::new, system);
  }

  // #initProjections

  // #sourceProvider
  // Split the slices into 4 ranges
  int numberOfSliceRanges = 4;
  List<Pair<Integer, Integer>> sliceRanges =
      EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);

  // Example of using the first slice range
  int minSlice = sliceRanges.get(0).first();
  int maxSlice = sliceRanges.get(0).second();
  String entityType = ShoppingCart.ENTITY_TYPE_KEY.name();

  SourceProvider<Offset, EventEnvelope<ShoppingCart.Event>> sourceProvider =
      EventSourcedProvider.eventsBySlices(
          system, R2dbcReadJournal.Identifier(), entityType, minSlice, maxSlice);

  // #sourceProvider

  {
    // #exactlyOnce
    ProjectionId projectionId =
        ProjectionId.of("ShoppingCarts", "carts-" + minSlice + "-" + maxSlice);

    Optional<R2dbcProjectionSettings> settings = Optional.empty();

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        R2dbcProjection.exactlyOnce(
            projectionId, settings, sourceProvider, ShoppingCartHandler::new, system);
    // #exactlyOnce
  }

  {
    // #atLeastOnce
    ProjectionId projectionId =
        ProjectionId.of("ShoppingCarts", "carts-" + minSlice + "-" + maxSlice);

    Optional<R2dbcProjectionSettings> settings = Optional.empty();

    int saveOffsetAfterEnvelopes = 100;
    Duration saveOffsetAfterDuration = Duration.ofMillis(500);

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        R2dbcProjection.atLeastOnce(
                projectionId, settings, sourceProvider, ShoppingCartHandler::new, system)
            .withSaveOffset(saveOffsetAfterEnvelopes, saveOffsetAfterDuration);
    // #atLeastOnce
  }

  {
    // #grouped
    ProjectionId projectionId =
        ProjectionId.of("ShoppingCarts", "carts-" + minSlice + "-" + maxSlice);

    Optional<R2dbcProjectionSettings> settings = Optional.empty();

    int saveOffsetAfterEnvelopes = 100;
    Duration saveOffsetAfterDuration = Duration.ofMillis(500);

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        R2dbcProjection.groupedWithin(
                projectionId, settings, sourceProvider, GroupedShoppingCartHandler::new, system)
            .withGroup(saveOffsetAfterEnvelopes, saveOffsetAfterDuration);
    // #grouped
  }

  {
    // #projectionSettings
    ProjectionId projectionId =
        ProjectionId.of("ShoppingCarts", "carts-" + minSlice + "-" + maxSlice);

    Optional<R2dbcProjectionSettings> settings =
        Optional.of(
            R2dbcProjectionSettings.create(
                system.settings().config().getConfig("second-projection-r2dbc")));

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        R2dbcProjection.atLeastOnce(
            projectionId, settings, sourceProvider, ShoppingCartHandler::new, system);
    // #projectionSettings
  }
}
