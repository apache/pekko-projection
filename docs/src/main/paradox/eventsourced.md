# Events from Apache Pekko Persistence

A typical source for Projections is events stored with @apidoc[EventSourcedBehavior$] in [Apache Pekko Persistence](https://pekko.apache.org/docs/pekko/current/typed/persistence.html). Events can be [tagged](https://pekko.apache.org/docs/pekko/current/typed/persistence.html#tagging) and then
consumed with the [eventsByTag query](https://pekko.apache.org/docs/pekko/current/persistence-query.html#eventsbytag-and-currenteventsbytag).

Apache Pekko Projections has integration with `eventsByTag`, which is described here. 

## Dependencies

To use the Event Sourced module of Apache Pekko Projections add the following dependency in your project:

@@dependency [sbt,Maven,Gradle] {
  group=org.apache.pekko
  artifact=pekko-projection-eventsourced_$scala.binary.version$
  version=$project.version$
}

Apache Pekko Projections require Akka $akka.version$ or later, see @ref:[Akka version](overview.md#akka-version).

@@project-info{ projectId="eventsourced" }

### Transitive dependencies

The table below shows `pekko-projection-eventsourced`'s direct dependencies and the second tab shows all libraries it depends on transitively.

@@dependencies{ projectId="eventsourced" }

## SourceProvider for eventsByTag

A @apidoc[SourceProvider] defines the source of the event envelopes that the `Projection` will process. A `SourceProvider`
for the `eventsByTag` query can be defined with the @apidoc[EventSourcedProvider$] like this:

Scala
:  @@snip [EventSourcedDocExample.scala](/examples/src/test/scala/docs/eventsourced/EventSourcedDocExample.scala) { #eventsByTagSourceProvider }

Java
:  @@snip [EventSourcedDocExample.java](/examples/src/test/java/jdocs/eventsourced/EventSourcedDocExample.java) { #eventsByTagSourceProvider }

This example is using the [Cassandra plugin for Apache Pekko Persistence](https://doc.akka.io/docs/akka-persistence-cassandra/current/read-journal.html),
but same code can be used for other Apache Pekko Persistence plugins by replacing the `CassandraReadJournal.Identifier`.
For example the [JDBC plugin](https://doc.akka.io/docs/akka-persistence-jdbc/current/) can be used. You will
use the same plugin as you have configured for the write side that is used by the `EventSourcedBehavior`.

This source is consuming all events from the `ShoppingCart` `EventSourcedBehavior` that are tagged with `"cart-1"`.

The tags are assigned as described in @ref:[Tagging Events in EventSourcedBehavior](running.md#tagging-events-in-eventsourcedbehavior).

The @scala[`EventEnvelope[ShoppingCart.Event]`]@java[`EventEnvelope<ShoppingCart.Event>`] is what the `Projection`
handler will process. It contains the `Event` and additional meta data, such as the offset that will be stored
by the `Projection`. See @apidoc[pekko.projection.eventsourced.EventEnvelope] for full details of what the
envelope contains. 

## SourceProvider for eventsBySlices

A @apidoc[SourceProvider] defines the source of the event envelopes that the `Projection` will process. A `SourceProvider`
for the `eventsBySlices` query can be defined with the @apidoc[EventSourcedProvider$] like this:

Scala
:  @@snip [EventSourcedDocExample.scala](/examples/src/test/scala/docs/eventsourced/EventSourcedDocExample.scala) { #eventsBySlicesSourceProvider }

Java
:  @@snip [EventSourcedDocExample.java](/examples/src/test/java/jdocs/eventsourced/EventSourcedBySlicesDocExample.java) { #eventsBySlicesSourceProvider }

This example is using the [R2DBC plugin for Apache Pekko Persistence](https://doc.akka.io/docs/akka-persistence-r2dbc/current/query.html).
You will use the same plugin as you have configured for the write side that is used by the `EventSourcedBehavior`.

This source is consuming all events from the `ShoppingCart` `EventSourcedBehavior` for the given slice range. In a production application, you would need to start as many instances as the number of slice ranges. That way you consume the events from all entities.

The @scala[`EventEnvelope[ShoppingCart.Event]`]@java[`EventEnvelope<ShoppingCart.Event>`] is what the `Projection`
handler will process. It contains the `Event` and additional meta data, such as the offset that will be stored
by the `Projection`. See @apidoc[pekko.persistence.query.typed.EventEnvelope] for full details of what the
envelope contains.
