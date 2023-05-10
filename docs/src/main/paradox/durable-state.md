# Changes from Durable State

A typical source for Projections is the change stored with @apidoc[DurableStateBehavior$] in [Apache Pekko Persistence](https://pekko.apache.org/docs/pekko/current/typed/durable-state/persistence.html). Durable state changes can be [tagged](https://pekko.apache.org/docs/pekko/current/typed/durable-state/persistence.html#tagging) and then
consumed with the [changes query](https://pekko.apache.org/docs/pekko/current/durable-state/persistence-query.html#using-query-with-pekko-projections).

Apache Pekko Projections has integration with `changes`, which is described here. 

## Dependencies

To use the Durable State module of Apache Pekko Projections, add the following dependency in your project:

@@dependency [sbt,Maven,Gradle] {
  group=org.apache.pekko
  artifact=pekko-projection-durable-state_$scala.binary.version$
  version=$project.version$
}

Apache Pekko Projections requires Pekko $pekko.version$ or later, see @ref:[Pekko version](overview.md#pekko-version).

@@project-info{ projectId="durable-state" }

### Transitive dependencies

The table below shows the `pekko-projection-durable-state` direct dependencies.The second tab shows all libraries it depends on transitively.

@@dependencies{ projectId="durable-state" }

## SourceProvider for changesByTag

A @apidoc[SourceProvider] defines the source of the envelopes that the `Projection` will process. A `SourceProvider`
for the `changes` query can be defined with the @apidoc[DurableStateStoreProvider$] like this:

Scala
:  @@snip [DurableStateStoreDocExample.scala](/examples/src/test/scala/docs/state/DurableStateStoreDocExample.scala) { #changesByTagSourceProvider }

Java
:  @@snip [DurableStateStoreDocExample.java](/examples/src/test/java/jdocs/state/DurableStateStoreDocExample.java) { #changesByTagSourceProvider }

This example is using the [DurableStateStore JDBC plugin for Apache Pekko Persistence](https://pekko.apache.org/docs/pekko-persistence-jdbc/current/durable-state-store.html).
You will use the same plugin that you configured for the write side. The one that is used by the `DurableStateBehavior`.

This source is consuming all the changes from the `Account` `DurableStateBehavior` that are tagged with `"bank-accounts-1"`. In a production application, you would need to start as many instances as the number of different tags you used. That way you consume the changes from all entities.

The @scala[`DurableStateChange[AccountEntity.Account]`]@java[`DurableStateChange<AccountEntity.Account>`] is what the `Projection`
handler will process. It contains the `State` and additional meta data, such as the offset that will be stored
by the `Projection`. See @apidoc[pekko.persistence.query.DurableStateChange] for full details of what it contains. 

## SourceProvider for changesBySlices

A @apidoc[SourceProvider] defines the source of the envelopes that the `Projection` will process. A `SourceProvider`
for the `changesBySlices` query can be defined with the @apidoc[DurableStateStoreProvider$] like this:

Scala
:  @@snip [DurableStateStoreDocExample.scala](/examples/src/test/scala/docs/state/DurableStateStoreDocExample.scala) { #changesBySlicesSourceProvider }

Java
:  @@snip [DurableStateStoreDocExample.java](/examples/src/test/java/jdocs/state/DurableStateStoreBySlicesDocExample.java) { #changesBySlicesSourceProvider }

This example is using the [R2DBC plugin for Apache Pekko Persistence](https://pekko.apache.org/docs/pekko-persistence-r2dbc/current/query.html).
You will use the same plugin that you configured for the write side. The one that is used by the `DurableStateBehavior`.

This source is consuming all the changes from the `Account` `DurableStateBehavior` for the given slice range. In a production application, you would need to start as many instances as the number of slice ranges. That way you consume the changes from all entities.

The @scala[`DurableStateChange[AccountEntity.Account]`]@java[`DurableStateChange<AccountEntity.Account>`] is what the `Projection`
handler will process. It contains the `State` and additional meta data, such as the offset that will be stored
by the `Projection`. See @apidoc[pekko.persistence.query.DurableStateChange] for full details of what it contains. 
