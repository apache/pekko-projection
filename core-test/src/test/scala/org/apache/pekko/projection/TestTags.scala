/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection

import org.scalatest.Tag

object TestTags {

  object InMemoryDb extends Tag("InMemoryDb")
  object ContainerDb extends Tag("ContainerDb")
  object FlakyDb extends Tag("FlakyDb")

}
