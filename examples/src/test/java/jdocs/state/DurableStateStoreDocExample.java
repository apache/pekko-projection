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

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

// #changesByTagSourceProvider
import org.apache.pekko.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
import org.apache.pekko.persistence.query.DurableStateChange;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.state.javadsl.DurableStateSourceProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;

// #changesByTagSourceProvider

public interface DurableStateStoreDocExample {

  public static void illustrateSourceProvider() {

    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

    // #changesByTagSourceProvider
    SourceProvider<Offset, DurableStateChange<AccountEntity.Account>> sourceProvider =
        DurableStateSourceProvider.changesByTag(
            system, JdbcDurableStateStore.Identifier(), "bank-accounts-1");
    // #changesByTagSourceProvider
  }
}
