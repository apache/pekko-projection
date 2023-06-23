/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.classic;

import org.apache.pekko.actor.typed.ActorSystem;

import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionBehavior;

// #import-adapter
import org.apache.pekko.actor.typed.javadsl.Adapter;

// #import-adapter

public interface ClassicDocExample {

  public static void illustrateSystem() {

    // #system
    org.apache.pekko.actor.ActorSystem system =
        org.apache.pekko.actor.ActorSystem.create("Example");
    ActorSystem<Void> typedSystem = Adapter.toTyped(system);
    // #system
  }

  public static void illustrateSpawn() {

    org.apache.pekko.actor.ActorSystem system =
        org.apache.pekko.actor.ActorSystem.create("Example");
    Projection<?> projection = null;

    // #spawn
    Adapter.spawn(system, ProjectionBehavior.create(projection), "theProjection");
    // #spawn
  }
}
