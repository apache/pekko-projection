/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

// #guideSetup
package jdocs.guide;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
// #guideSetup

// #guideSourceProviderImports
import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
// #guideSourceProviderImports

// #guideProjectionImports
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.cassandra.javadsl.CassandraProjection;
import org.apache.pekko.projection.javadsl.AtLeastOnceProjection;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSession;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSessionRegistry;
// #guideProjectionImports

// #guideSetup

public class ShoppingCartApp {
  public static void main(String[] args) throws Exception {
    Config config = ConfigFactory.load("guide-shopping-cart-app.conf");

    ActorSystem.create(
        Behaviors.setup(
            context -> {
              ActorSystem<Void> system = context.getSystem();

              // ...

              // #guideSetup
              // #guideSourceProviderSetup
              SourceProvider<Offset, EventEnvelope<ShoppingCartEvents.Event>> sourceProvider =
                  EventSourcedProvider.eventsByTag(
                      system, CassandraReadJournal.Identifier(), ShoppingCartTags.SINGLE);
              // #guideSourceProviderSetup

              // #guideProjectionSetup
              CassandraSession session =
                  CassandraSessionRegistry.get(system)
                      .sessionFor("pekko.projection.cassandra.session-config");
              ItemPopularityProjectionRepositoryImpl repo =
                  new ItemPopularityProjectionRepositoryImpl(session);
              AtLeastOnceProjection<Offset, EventEnvelope<ShoppingCartEvents.Event>> projection =
                  CassandraProjection.atLeastOnce(
                      ProjectionId.of("shopping-carts", ShoppingCartTags.SINGLE),
                      sourceProvider,
                      () ->
                          new ItemPopularityProjectionHandler(
                              ShoppingCartTags.SINGLE, system, repo));

              context.spawn(ProjectionBehavior.create(projection), projection.projectionId().id());
              // #guideProjectionSetup

              // #guideSetup
              return Behaviors.empty();
            }),
        "ShoppingCartApp",
        config);
  }
}
// #guideSetup
