/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.classic

import org.apache.pekko
import pekko.projection.Projection
import pekko.projection.ProjectionBehavior

object ClassicDocExample {

  object IllustrateSystem {
    // #system
    import org.apache.pekko
    import pekko.actor.typed.scaladsl.adapter._

    private val system = pekko.actor.ActorSystem("Example")
    private val typedSystem: pekko.actor.typed.ActorSystem[_] = system.toTyped
    // #system

    typedSystem.terminate() // avoid unused warning
  }

  object IllustrateSpawn {
    private val system = pekko.actor.ActorSystem("Example")
    private val projection: Projection[Any] = null

    // #spawn
    import org.apache.pekko.actor.typed.scaladsl.adapter._

    system.spawn(ProjectionBehavior(projection), "theProjection")
    // #spawn
  }
}
