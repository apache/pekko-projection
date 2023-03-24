# Akka Classic

Apache Pekko Projections can be used with the [new Actor API](https://pekko.apache.org/docs/pekko/current/typed/actors.html) or
the [classic Actor API](https://pekko.apache.org/docs/pekko/current/index-classic.html). The documentation samples
show the new Actor API, and this page highlights how to use it with the classic Actor API.

## Actor System

The `ActorSystem` is a parameter in several places of the Projections API. That is the `org.apache.pekko.actor.typed.ActorSystem`.
Given a classic `org.apache.pekko.actor.ActorSystem` it can be adapted to an `org.apache.pekko.actor.typed.ActorSystem` like this:

Scala
:  @@snip [ClassicDocExample.scala](/examples/src/test/scala/docs/classic/ClassicDocExample.scala) { #system }

Java
:  @@snip [ClassicDocExample.java](/examples/src/test/java/jdocs/classic/ClassicDocExample.java) { #import-adapter #system }

## PersistentActor

@ref:[Events from Akka Classic Persistence](eventsourced.md) can be emitted from `PersistentActor` and consumed by a
Projection with the @apidoc[EventSourcedProvider$]. The events from the `PersistentActor` must be tagged by wrapping
them in `org.apache.pekko.persistence.journal.Tagged`, which can be done in the `PersistentActor` or by using
[Event Adapters](https://pekko.apache.org/docs/pekko/current/persistence.html#event-adapters).

## Running

As described in @ref:[Running a Projection](running.md) the Projection is typically run with a Sharded Daemon Process.
`ShardedDaemonProcess` can be used in the same way with a classic `org.apache.pekko.actor.ActorSystem`, after adapting it to
`org.apache.pekko.actor.typed.ActorSystem` as described @ref:[above](#actor-system).

To @ref:[run with a local actor](running.md#running-with-local-actor) the `ProjectionBehavior` can be
spawned from the classic `ActorSystem` or a classic `Actor`:

Scala
:  @@snip [ClassicDocExample.scala](/examples/src/test/scala/docs/classic/ClassicDocExample.scala) { #spawn }

Java
:  @@snip [ClassicDocExample.java](/examples/src/test/java/jdocs/classic/ClassicDocExample.java) { #import-adapter #spawn }




