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

package org.apache.pekko.projection.grpc.internal

import java.time.Instant

import org.apache.pekko
import pekko.persistence.query.TimestampOffset
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.typed.PersistenceId
import pekko.projection.grpc.internal.FilterStage.Filter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class FilterSpec extends AnyWordSpecLike with Matchers {

  private def createEnvelope(pid: String, tags: Set[String] = Set.empty): EventEnvelope[Any] = {
    val slice = math.abs(pid.hashCode % 1024)
    val seqNr = 1
    val now = Instant.now()
    EventEnvelope(
      TimestampOffset(Instant.now, Map(pid -> seqNr)),
      pid,
      seqNr,
      "evt",
      now.toEpochMilli,
      PersistenceId.extractEntityType(pid),
      slice,
      filtered = false,
      source = "",
      tags = tags)
  }

  "Filter" should {
    "include with empty filter" in {
      Filter.empty.matches(createEnvelope("pid")) shouldBe true
    }

    "honor include after exclude" in {
      val filter =
        Filter.empty
          .addIncludePersistenceIds(Set("pid-1", "pid-3"))
          .addExcludePersistenceIds(Set("pid-3", "pid-2"))
          .addIncludeTags(Set("a"))
          .addExcludeTags(Set("a", "b"))
      filter.matches(createEnvelope("pid-1")) shouldBe true
      filter.matches(createEnvelope("pid-2")) shouldBe false
      filter.matches(createEnvelope("pid-3")) shouldBe true
      filter.matches(createEnvelope("pid-4", tags = Set("b"))) shouldBe false
      filter.matches(createEnvelope("pid-5", tags = Set("a"))) shouldBe true
      filter.matches(createEnvelope("pid-6", tags = Set("a", "b"))) shouldBe true
    }

    "exclude with regexp" in {
      val filter =
        Filter.empty.addIncludePersistenceIds(Set("Entity|a-1", "Entity|a-2")).addExcludeRegexEntityIds(List("a-.*"))
      filter.matches(createEnvelope("Entity|a-1")) shouldBe true
      filter.matches(createEnvelope("Entity|a-2")) shouldBe true
      filter.matches(createEnvelope("Entity|a-3")) shouldBe false
      filter.matches(createEnvelope("Entity|b-1")) shouldBe true
    }

    "remove criteria" in {
      val filter =
        Filter.empty.addIncludePersistenceIds(Set("pid-1", "pid-3")).addExcludePersistenceIds(Set("pid-3", "pid-2"))
      filter.matches(createEnvelope("pid-1")) shouldBe true
      filter.matches(createEnvelope("pid-3")) shouldBe true
      val filter2 = filter.removeIncludePersistenceIds(Set("pid-3"))
      filter2.matches(createEnvelope("pid-3")) shouldBe false
    }
  }

}
