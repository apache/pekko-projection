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

package org.apache.pekko.projection.grpc.replication.javadsl

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.annotation.ApiMayChange
import pekko.japi.function.{ Function => JFunction }
import pekko.persistence.typed.javadsl.EventSourcedBehavior
import pekko.persistence.typed.javadsl.ReplicationContext

/**
 * Dynamically provides factory methods for creating replicated event sourced behaviors.
 *
 * Must be used to create an event sourced behavior to be replicated with `Replication.grpcReplication`.
 *
 * Can optionally be composed with other Behavior factories, to get access to actor context or timers.
 */
@ApiMayChange
abstract class ReplicatedBehaviors[Command, Event, State] {
  def setup(factory: JFunction[ReplicationContext, EventSourcedBehavior[Command, Event, State]]): Behavior[Command]
}
