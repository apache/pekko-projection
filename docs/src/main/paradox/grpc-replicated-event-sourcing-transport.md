# Pekko Replicated Event Sourcing over gRPC

Pekko Replicated Event Sourcing extends Pekko Persistence allowing multiple replicas of the same entity, all accepting
writes, for example in different data centers or cloud provider regions. This makes it possible to implement patterns
such as active-active and hot standby.

Originally, Pekko Replicated Event Sourcing has required cross-replica access to the underlying replica database, which
can be hard to open up for security and infrastructure reasons. It was also easiest to use in an
:[Apache Pekko Multi DC Cluster](pekko:typed/cluster-dc.html) setup
where a single cluster spans multiple datacenters or regions, another thing that can be complicated to allow.

Pekko Replicated Event Sourcing over gRPC builds on @ref:[Apache Pekko Projection gRPC](grpc.md) and @extref:[Apache Pekko gRPC](pekko-grpc:index.html) to instead use gRPC as the cross-replica transport for events.

There are no requirements that the replicas are sharing a cluster, instead it is expected that each replica is a separate
Pekko cluster with the gRPC replication transport as only connection in between.

@@@ warning

This module is currently marked as @extref:[May Change](pekko:common/may-change.html)
in the sense that the API might be changed based on feedback from initial usage.
However, the module is ready for usage in production and we will not break serialization format of
messages or stored data.

@@@

## Overview

For a basic overview of Replicated Event Sourcing see the @extref:[Apache Pekko Replicated Event Sourcing docs](pekko:typed/replicated-eventsourcing.html)

Pekko Replicated Event Sourcing over gRPC consists of the following three parts:

* The Replicated Event Sourced Behavior is run in each replica as a sharded entity using @extref:[Apache Pekko Cluster
  Sharding](pekko:typed/cluster-sharding.html).

* The events of each replica are published to the other replicas using @ref:[Apache Pekko Projection gRPC](grpc.md) endpoints.

* Each replica consumes a number of parallel slices of the events from each other replica by running Pekko Projection
  gRPC in @extref:[Apache Pekko Sharded Daemon Process](pekko:typed/cluster-sharded-daemon-process.html).


## Dependencies

The functionality is provided through the `pekko-projection-grpc` module.

@@project-info{ projectId="grpc" }

To use the gRPC module of Apache Pekko Projections add the following dependency in your project:

@@dependency [sbt,Maven,Gradle] {
  group=org.apache.pekko
  artifact=pekko-projection-grpc_$scala.binary.version$
  version=$project.version$
}

Pekko Replicated Event Sourcing over gRPC can only be run in an Apache Pekko cluster since it uses cluster components.

It is currently only possible to use @ref:[Apache Pekko-projection-r2dbc](r2dbc.md) as the
projection storage and journal for this module.

The full set of dependencies needed:

@@dependency [sbt,Maven,Gradle] {
group=org.apache.pekko
artifact=pekko-projection-grpc_$scala.binary.version$
version=$project.version$
group2=org.apache.pekko
artifact2=pekko-cluster-typed_$scala.binary.version$
version2=$pekko.version$
group3=org.apache.pekko
artifact3=pekko-cluster-sharding-typed_$scala.binary.version$
version3=$pekko.version$
group4=org.apache.pekko
artifact4=pekko-persistence-r2dbc_$scala.binary.version$
version4=$pekko.r2dbc.version$
group5=org.apache.pekko
artifact5=pekko-projection-r2dbc_$scala.binary.version$
version5=$pekko.r2dbc.version$
}

### Transitive dependencies

The table below shows `pekko-projection-grpc`'s direct dependencies, and the second tab shows all libraries it depends on transitively.

@@dependencies{ projectId="grpc" }

## API and setup

The same API as regular `EventSourcedBehavior`s is used to define the logic. See @extref:[Replicated Event Sourcing](pekko:typed/replicated-eventsourcing.html) for more detail on designing an entity for replication.

To enable an entity for Replicated Event Sourcing over gRPC, use the @apidoc[Replication$] `grpcReplication` method,
which takes @apidoc[ReplicationSettings], a factory function for the behavior, and an actor system.

The factory function will be passed a @apidoc[ReplicatedBehaviors] factory that must be used to set up the replicated
event sourced behavior. Its `setup` method provides a @apidoc[ReplicationContext] to create an `EventSourcedBehavior`
which will then be configured for replication. The behavior factory can be composed with other behavior factories, if
access to the actor context or timers are needed.

### Settings

The @apidoc[pekko.projection.grpc.replication.*.ReplicationSettings] @scala[`apply`]@java[`create`] factory methods can
accept an entity name, a @apidoc[ReplicationProjectionProvider] and an actor system. The configuration of that system
is expected to have a top level entry with the entity name containing this structure:

Scala
:  @@snip [config](/grpc-test/src/test/scala/org/apache/pekko/projection/grpc/replication/ReplicationSettingsSpec.scala) { #config }

Java
:  @@snip [config](/grpc-test/src/test/scala/org/apache/pekko/projection/grpc/replication/ReplicationSettingsSpec.scala) { #config }

The entries in the block refer to the local replica while `replicas` is a list of all replicas, including the node itself,
with details about how to reach the replicas across the network.

The `grpc.client` section for each of the replicas is used for setting up the Apache Pekko gRPC client and supports the same discovery, TLS
and other connection options as when using Apache Pekko gRPC directly. For more details see @extref:[Apache Pekko gRPC configuration](pekko-grpc:client/configuration.html#by-configuration).

It is also possible to set up @apidoc[pekko.projection.grpc.replication.*.ReplicationSettings] through APIs only and not rely
on the configuration file at all.

### Binding the publisher

Binding the publisher is a manual step to allow arbitrary customization of the Apache Pekko HTTP server and combining the endpoint
with other HTTP and gRPC routes.

When there is only a single replicated entity and no other usage of Apache Pekko gRPC Projections in an application a
convenience is provided through `createSingleServiceHandler` on @apidoc[pekko.projection.grpc.replication.*.Replication]
which will create a single handler.

When multiple producers exist, all instances of @apidoc[pekko.projection.grpc.producer.EventProducerSettings] need to
be passed at once to `EventProducer.grpcServiceHandler` to create a single producer service handling each of the event
streams.

Scala
:  @@snip [ProducerApiSample.scala](/grpc-test/src/test/scala/org/apache/pekko/projection/grpc/replication/scaladsl/ProducerApiSample.scala) { #multi-service }

Java
:  @@snip [ReplicationCompileTest.java](/grpc-test/src/test/java/org/apache/pekko/projection/grpc/replication/javdsl/ReplicationCompileTest.java) { #multi-service }


The Pekko HTTP server must be running with HTTP/2. This is the default since Pekko HTTP 2.0.0.

### Serialization of events

The events are serialized for being passed over the wire using the same Pekko serializer as configured for serializing
the events for storage.

Note that having separate replicas increases the risk that two different serialized formats and versions of the serializer
are running at the same time, so extra care must be taken when changing the events and their serialization and deploying
new versions of the application to the replicas.

For some scenarios it may be necessary to do a two-step deploy of format changes to not lose data, first deploy support
for a new serialization format so that all replicas can deserialize it, then a second deploy where the new field is actually
populated with data.

## Filters

By default, events from all Replicated Event Sourced entities are replicated.

The same kind of filters as described in @ref:[Apache Pekko Projection gRPC Filters](grpc.md#filters) can be used for
Replicated Event Sourcing.

Consumer defined filters are updated as described in @ref:[Apache Pekko Projection gRPC Consumer defined filter](grpc.md#consumer-defined-filter)

One thing to note is that `streamId` is always the same as the `entityType` when using Replicated Event Sourcing.

The entity id based filter criteria must include the replica id as suffix to the entity id, with `|` separator.

Replicated Event Sourcing is bidirectional replication, and therefore you would typically have to define the same
filters on both sides. That is not handled automatically.

## Sample projects

Source code and build files for complete sample projects can be found in the `apache/pekko-projection` GitHub repository.


## Access control

### From the consumer

The consumer can pass metadata, such as auth headers, in each request to the producer service by specifying @apidoc[pekko.grpc.*.Metadata] as `additionalRequestMetadata` when creating each @apidoc[pekko.projection.grpc.replication.*.Replica]

### In the producer

Authentication and authorization for the producer can be done by implementing an @apidoc[EventProducerInterceptor] and passing
it to the `grpcServiceHandler` method during producer bootstrap. The interceptor is invoked with the stream id and
gRPC request metadata for each incoming request and can return a suitable error through @apidoc[GrpcServiceException]
