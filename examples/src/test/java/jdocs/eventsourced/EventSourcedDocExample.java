/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.eventsourced;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

// #eventsByTagSourceProvider
import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;

// #eventsByTagSourceProvider

public interface EventSourcedDocExample {

  public static void illustrateEventsByTagSourceProvider() {

    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

    // #eventsByTagSourceProvider
    SourceProvider<Offset, EventEnvelope<ShoppingCart.Event>> sourceProvider =
        EventSourcedProvider.eventsByTag(system, CassandraReadJournal.Identifier(), "carts-1");
    // #eventsByTagSourceProvider

  }
}
