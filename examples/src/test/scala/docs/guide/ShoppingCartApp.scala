/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

//#guideSetup
package docs.guide

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.projection.ProjectionBehavior
import pekko.projection.eventsourced.EventEnvelope
import com.typesafe.config.ConfigFactory
//#guideSetup

//#guideSourceProviderImports
import org.apache.pekko
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.query.Offset
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.projection.scaladsl.SourceProvider
//#guideSourceProviderImports

//#guideProjectionImports
import org.apache.pekko
import pekko.projection.ProjectionId
import pekko.projection.cassandra.scaladsl.CassandraProjection
import pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
//#guideProjectionImports

//#guideSetup

object ShoppingCartApp extends App {
  val config = ConfigFactory.load("guide-shopping-cart-app.conf")

  ActorSystem(
    Behaviors.setup[String] { context =>
      val system = context.system

      // ...

      // #guideSetup
      // #guideSourceProviderSetup
      val sourceProvider: SourceProvider[Offset, EventEnvelope[ShoppingCartEvents.Event]] =
        EventSourcedProvider
          .eventsByTag[ShoppingCartEvents.Event](
            system,
            readJournalPluginId = CassandraReadJournal.Identifier,
            tag = ShoppingCartTags.Single)
      // #guideSourceProviderSetup

      // #guideProjectionSetup
      implicit val ec = system.executionContext
      val session = CassandraSessionRegistry(system).sessionFor("pekko.projection.cassandra.session-config")
      val repo = new ItemPopularityProjectionRepositoryImpl(session)
      val projection = CassandraProjection.atLeastOnce(
        projectionId = ProjectionId("shopping-carts", ShoppingCartTags.Single),
        sourceProvider,
        handler = () => new ItemPopularityProjectionHandler(ShoppingCartTags.Single, system, repo))

      context.spawn(ProjectionBehavior(projection), projection.projectionId.id)
      // #guideProjectionSetup

      // #guideSetup
      Behaviors.empty
    },
    "ShoppingCartApp",
    config)
}
//#guideSetup
