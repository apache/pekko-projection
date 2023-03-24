/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.state

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors

object DurableStateStoreDocExample {

  private val system = ActorSystem[Nothing](Behaviors.empty, "Example")

  object IllustrateEventsByTagSourceProvider {
    // #changesByTagSourceProvider
    import org.apache.pekko
    import pekko.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    import pekko.persistence.query.DurableStateChange
    import pekko.persistence.query.Offset
    import pekko.projection.state.scaladsl.DurableStateSourceProvider
    import pekko.projection.scaladsl.SourceProvider

    val sourceProvider: SourceProvider[Offset, DurableStateChange[AccountEntity.Account]] =
      DurableStateSourceProvider
        .changesByTag[AccountEntity.Account](system, JdbcDurableStateStore.Identifier, "bank-accounts-1")
    // #changesByTagSourceProvider
  }

  object IllustrateEventsBySlicesSourceProvider {
    object R2dbcDurableStateStore {
      val Identifier = "pekko.persistence.r2dbc.query"
    }

    // #changesBySlicesSourceProvider
    import org.apache.pekko
    import pekko.persistence.query.DurableStateChange
    import pekko.persistence.query.Offset
    import pekko.projection.state.scaladsl.DurableStateSourceProvider
    import pekko.projection.scaladsl.SourceProvider

    // Slit the slices into 4 ranges
    val numberOfSliceRanges: Int = 4
    val sliceRanges =
      DurableStateSourceProvider.sliceRanges(system, R2dbcDurableStateStore.Identifier, numberOfSliceRanges)

    // Example of using the first slice range
    val minSlice: Int = sliceRanges.head.min
    val maxSlice: Int = sliceRanges.head.max
    val entityType: String = "Account"

    val sourceProvider: SourceProvider[Offset, DurableStateChange[AccountEntity.Account]] =
      DurableStateSourceProvider
        .changesBySlices[AccountEntity.Account](
          system,
          R2dbcDurableStateStore.Identifier,
          entityType,
          minSlice,
          maxSlice)
    // #changesBySlicesSourceProvider
  }
}
