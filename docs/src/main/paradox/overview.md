# Overview

The purpose of Apache Pekko Projections is described in @ref:[Use Cases](use-cases.md).

In Apache Pekko Projections you process a stream of events or records from a source to a projected model or external system.
Each event is associated with an offset representing the position in the stream. This offset is used for
resuming the stream from that position when the projection is restarted.

As the source you can select from:

* @ref:[Events from Apache Pekko Persistence](eventsourced.md)
* @ref:[State changes from Apache Pekko Persistence](durable-state.md)
* @ref:[Messages from Kafka](kafka.md)
* Building your own @apidoc[SourceProvider]

For the offset storage you can select from:

* @ref:[Offset in Cassandra](cassandra.md)
* @ref:[Offset in a relational DB with JDBC](jdbc.md)
* @ref:[Offset in a relational DB with Slick](slick.md) (community-driven module)

Those building blocks are assembled into a `Projection`. You can have many instances of it
@ref:[automatically distributed and run](running.md) in an Apache Pekko Cluster.

@@@ warning

This module is currently marked as [May Change](https://pekko.apache.org/docs/pekko/current/common/may-change.html)
in the sense that the API might be changed based on feedback from initial usage.
However, the module is ready for usage in production and we will not break serialization format of 
messages or stored data.

@@@

To see a complete example of an Apache Pekko Projections implementation review the @ref:[Getting Started Guide](getting-started/index.md).

## Dependencies

Apache Pekko Projections consists of several modules for specific technologies. The dependency section for
each module describes which dependency you should define in your project.

* @ref:[Events from Apache Pekko Persistence](eventsourced.md)
* @ref:[State changes from Apache Pekko Persistence](durable-state.md)
* @ref:[Messages from Kafka](kafka.md)
* @ref:[Offset in Cassandra](cassandra.md)
* @ref:[Offset in a relational DB with JDBC](jdbc.md)
* @ref:[Offset in a relational DB with Slick](slick.md) (community-driven module)

All of them share a dependency to `pekko-projection-core`: 

@@dependency [sbt,Maven,Gradle] {
  group=org.apache.pekko
  artifact=pekko-projection-core_$scala.binary.version$
  version=$project.version$
}

@@project-info{ projectId="core" }

### Pekko version

Apache Pekko Projections requires **Pekko $pekko.version$** or later. See [Pekko's Binary Compatibility Rules](https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html) for details.

It is recommended to use the latest patch version of Pekko. 
It is important all Pekko dependencies are in the same version, so it is recommended to depend on
them explicitly to avoid problems with transient dependencies causing an unlucky mix of versions. For example:

@@dependency[sbt,Gradle,Maven] {
  symbol=PekkoVersion
  value=$pekko.version$
  group=org.apache.pekko
  artifact=pekko-cluster-sharding-typed_$scala.binary.version$
  version=PekkoVersion
  group2=org.apache.pekko
  artifact2=pekko-persistence-query_$scala.binary.version$
  version2=PekkoVersion
  group3=org.apache.pekko
  artifact3=pekko-discovery_$scala.binary.version$
  version3=PekkoVersion
}

### Transitive dependencies

The table below shows `pekko-projection-core`'s direct dependencies and the second tab shows all libraries it depends on transitively.

@@dependencies{ projectId="core" }

See the individual modules for their transitive dependencies.

### Pekko Classic

Apache Pekko Projections can be used with the [new Actor API](https://pekko.apache.org/docs/pekko/current/typed/actors.html) or
the [classic Actor API](https://pekko.apache.org/docs/pekko/current/index-classic.html). The documentation samples
show the new Actor API, and the @ref:[Pekko Classic page](classic.md) highlights how to use it with the classic
Actor API.

## Contributing

Please feel free to contribute to Apache Pekko and Apache Pekko Projections by reporting issues you identify, or by suggesting changes to the code. Please refer to our [contributing instructions](https://github.com/akka/akka/blob/master/CONTRIBUTING.md) to learn how it can be done.

We want Pekko to strive in a welcoming and open atmosphere and expect all contributors to respect our [code of conduct](https://www.apache.org/foundation/policies/conduct.html).
