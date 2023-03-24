/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

//#guideClusterSetup
package docs.guide

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.query.Offset
import pekko.projection.ProjectionBehavior
import pekko.projection.ProjectionId
import pekko.projection.cassandra.scaladsl.CassandraProjection
import pekko.projection.eventsourced.EventEnvelope
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import com.typesafe.config.ConfigFactory

object ShoppingCartClusterApp extends App {
  val port = args.headOption match {
    case Some(portString) if portString.matches("""\d+""") => portString.toInt
    case _                                                 => throw new IllegalArgumentException("A pekko cluster port argument is required")
  }

  val config = ConfigFactory
    .parseString(s"pekko.remote.artery.canonical.port = $port")
    .withFallback(ConfigFactory.load("guide-shopping-cart-cluster-app.conf"))

  ActorSystem(
    Behaviors.setup[String] { context =>
      val system = context.system
      implicit val ec = system.executionContext
      val session = CassandraSessionRegistry(system).sessionFor("pekko.projection.cassandra.session-config")
      val repo = new ItemPopularityProjectionRepositoryImpl(session)

      def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[ShoppingCartEvents.Event]] =
        EventSourcedProvider
          .eventsByTag[ShoppingCartEvents.Event](
            system,
            readJournalPluginId = CassandraReadJournal.Identifier,
            tag = tag)

      def projection(tag: String) =
        CassandraProjection.atLeastOnce(
          projectionId = ProjectionId("shopping-carts", tag),
          sourceProvider(tag),
          handler = () => new ItemPopularityProjectionHandler(tag, system, repo))

      ShardedDaemonProcess(system).init[ProjectionBehavior.Command](
        name = "shopping-carts",
        numberOfInstances = ShoppingCartTags.Tags.size,
        behaviorFactory = (i: Int) => ProjectionBehavior(projection(ShoppingCartTags.Tags(i))),
        stopMessage = ProjectionBehavior.Stop)

      Behaviors.empty
    },
    "ShoppingCartClusterApp",
    config)
}
//#guideClusterSetup
