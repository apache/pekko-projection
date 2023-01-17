# Apache Pekko Projections

Apache Pekko Projections provides an abstraction for consuming a stream of `Envelope` (where `Envelope` contains a payload and a trackable offset). This streams can originate from persisted events, Kafka topics,
or other Apache Pekko connectors. 

Apache Pekko Projections also provides tools to track, restart and distribute these projections.
