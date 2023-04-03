/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.state;

import java.util.List;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

// #changesBySlicesSourceProvider
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.query.DurableStateChange;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.state.javadsl.DurableStateSourceProvider;

// #changesBySlicesSourceProvider

public interface DurableStateStoreBySlicesDocExample {
  public static class R2dbcDurableStateStore {
    public static String Identifier() {
      return "pekko.persistence.r2dbc.query";
    }
  }

  public static void illustrateSourceProvider() {

    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

    // #changesBySlicesSourceProvider
    // Slit the slices into 4 ranges
    int numberOfSliceRanges = 4;
    List<Pair<Integer, Integer>> sliceRanges =
        EventSourcedProvider.sliceRanges(
            system, R2dbcDurableStateStore.Identifier(), numberOfSliceRanges);

    // Example of using the first slice range
    int minSlice = sliceRanges.get(0).first();
    int maxSlice = sliceRanges.get(0).second();
    String entityType = "MyEntity";

    SourceProvider<Offset, DurableStateChange<AccountEntity.Account>> sourceProvider =
        DurableStateSourceProvider.changesBySlices(
            system, R2dbcDurableStateStore.Identifier(), entityType, minSlice, maxSlice);
    // #changesBySlicesSourceProvider
  }
}
