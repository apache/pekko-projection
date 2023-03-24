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
