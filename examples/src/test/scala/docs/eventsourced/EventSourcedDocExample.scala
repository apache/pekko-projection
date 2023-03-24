/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.eventsourced

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors

object EventSourcedDocExample {

  private val system = ActorSystem[Nothing](Behaviors.empty, "Example")

  object IllustrateEventsByTagSourceProvider {
    // #eventsByTagSourceProvider
    import org.apache.pekko
    import pekko.projection.eventsourced.EventEnvelope
    import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
    import pekko.persistence.query.Offset
    import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
    import pekko.projection.scaladsl.SourceProvider

    val sourceProvider: SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider
        .eventsByTag[ShoppingCart.Event](system, readJournalPluginId = CassandraReadJournal.Identifier, tag = "carts-1")
    // #eventsByTagSourceProvider
  }

  object IllustrateEventsBySlicesSourceProvider {
    object R2dbcReadJournal {
      val Identifier = "pekko.persistence.r2dbc.query"
    }

    // #eventsBySlicesSourceProvider
    import org.apache.pekko
    import pekko.persistence.query.typed.EventEnvelope
    import pekko.persistence.query.Offset
    import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
    import pekko.projection.scaladsl.SourceProvider

    // Slit the slices into 4 ranges
    val numberOfSliceRanges: Int = 4
    val sliceRanges = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, numberOfSliceRanges)

    // Example of using the first slice range
    val minSlice: Int = sliceRanges.head.min
    val maxSlice: Int = sliceRanges.head.max
    val entityType: String = "ShoppingCart"

    val sourceProvider: SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider
        .eventsBySlices[ShoppingCart.Event](
          system,
          readJournalPluginId = R2dbcReadJournal.Identifier,
          entityType,
          minSlice,
          maxSlice)
    // #eventsBySlicesSourceProvider
  }
}
