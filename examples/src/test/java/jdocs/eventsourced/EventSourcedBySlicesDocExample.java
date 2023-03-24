/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.eventsourced;

import java.util.List;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
// #eventsBySlicesSourceProvider
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;

// #eventsBySlicesSourceProvider

public interface EventSourcedBySlicesDocExample {

  public static class R2dbcReadJournal {
    public static String Identifier() {
      return "pekko.persistence.r2dbc.query";
    }
  }

  public static void illustrateEventsSlicesSourceProvider() {

    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

    // #eventsBySlicesSourceProvider
    // Slit the slices into 4 ranges
    int numberOfSliceRanges = 4;
    List<Pair<Integer, Integer>> sliceRanges =
        EventSourcedProvider.sliceRanges(
            system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);

    // Example of using the first slice range
    int minSlice = sliceRanges.get(0).first();
    int maxSlice = sliceRanges.get(0).second();
    String entityType = "MyEntity";

    SourceProvider<Offset, EventEnvelope<ShoppingCart.Event>> sourceProvider =
        EventSourcedProvider.eventsBySlices(
            system, R2dbcReadJournal.Identifier(), entityType, minSlice, maxSlice);
    // #eventsBySlicesSourceProvider

  }
}
