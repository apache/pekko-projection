/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

// #guideClusterSetup
package jdocs.guide;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.cassandra.javadsl.CassandraProjection;
import org.apache.pekko.projection.javadsl.AtLeastOnceProjection;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSession;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSessionRegistry;

public class ShoppingCartClusterApp {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("A pekko cluster port argument is required");
    }

    String portString = args[0];
    int port = Integer.parseInt(portString);

    Config config =
        ConfigFactory.parseString("pekko.remote.artery.canonical.port = " + port)
            .withFallback(ConfigFactory.load("guide-shopping-cart-cluster-app.conf"));

    ActorSystem.create(
        Behaviors.setup(
            context -> {
              ActorSystem<Void> system = context.getSystem();

              CassandraSession session =
                  CassandraSessionRegistry.get(system)
                      .sessionFor("pekko.projection.cassandra.session-config");
              ItemPopularityProjectionRepositoryImpl repo =
                  new ItemPopularityProjectionRepositoryImpl(session);

              ShardedDaemonProcess.get(system)
                  .init(
                      ProjectionBehavior.Command.class,
                      "shopping-carts",
                      ShoppingCartTags.TAGS.length,
                      n ->
                          ProjectionBehavior.create(
                              projection(system, repo, ShoppingCartTags.TAGS[n])),
                      ProjectionBehavior.stopMessage());

              return Behaviors.empty();
            }),
        "ShoppingCartClusterApp",
        config);
  }

  static SourceProvider<Offset, EventEnvelope<ShoppingCartEvents.Event>> sourceProvider(
      ActorSystem<?> system, String tag) {
    return EventSourcedProvider.eventsByTag(system, CassandraReadJournal.Identifier(), tag);
  }

  static AtLeastOnceProjection<Offset, EventEnvelope<ShoppingCartEvents.Event>> projection(
      ActorSystem<?> system, ItemPopularityProjectionRepository repo, String tag) {
    return CassandraProjection.atLeastOnce(
        ProjectionId.of("shopping-carts", tag),
        sourceProvider(system, tag),
        () -> new ItemPopularityProjectionHandler(tag, system, repo));
  }
}
// #guideClusterSetup
