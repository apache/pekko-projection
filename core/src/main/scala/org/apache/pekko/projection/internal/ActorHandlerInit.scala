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

package org.apache.pekko.projection.internal

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[projection] trait ActorHandlerInit[T] {
  private var actor: Option[ActorRef[T]] = None

  /** INTERNAL API */
  @InternalApi private[projection] def behavior: Behavior[T]

  /** INTERNAL API */
  final private[projection] def setActor(ref: ActorRef[T]): Unit =
    actor = Some(ref)

  /** INTERNAL API */
  final private[projection] def getActor(): ActorRef[T] = {
    actor match {
      case Some(ref) => ref
      case None      =>
        throw new IllegalStateException(
          "Actor not started, please report issue at " +
          "https://github.com/apache/pekko-projection/issues")
    }
  }
}
