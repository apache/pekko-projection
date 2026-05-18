/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.replication.scaladsl

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.Http
import pekko.projection.grpc.producer.scaladsl.EventProducer
import pekko.projection.grpc.producer.scaladsl.EventProducer.EventProducerSource

object ProducerApiSample {

  trait MyCommand
  object MyReplicatedBehavior {
    def apply(r: ReplicatedBehaviors[MyCommand, String, String]): Behavior[MyCommand] = ???
  }

  def otherReplication: Replication[Unit] = ???
  def multiEventProducers(settings: ReplicationSettings[MyCommand], host: String, port: Int)(
      implicit system: ActorSystem[_]): Unit = {
    // #multi-service
    val replication: Replication[MyCommand] =
      Replication.grpcReplication(settings)(MyReplicatedBehavior.apply)

    val allSources: Set[EventProducerSource] = {
      Set(
        replication.eventProducerService,
        // producers from other replicated entities or gRPC projections
        otherReplication.eventProducerService)
    }
    val route = EventProducer.grpcServiceHandler(allSources)

    val handler = ServiceHandler.concatOrNotFound(route)
    // #multi-service

    val _ = Http(system).newServerAt(host, port).bind(handler)
  }

}
