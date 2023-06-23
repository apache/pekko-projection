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

package org.apache.pekko.projection.jdbc

import org.apache.pekko.annotation.ApiMayChange

@ApiMayChange trait JdbcHandlerLifecycle {

  /**
   * Invoked when the projection is starting, before first envelope is processed.
   * Can be overridden to implement initialization.
   */
  def start(): Unit = ()

  /**
   * Invoked when the projection has been stopped. Can be overridden to implement resource
   * cleanup.
   */
  def stop(): Unit = ()
}
