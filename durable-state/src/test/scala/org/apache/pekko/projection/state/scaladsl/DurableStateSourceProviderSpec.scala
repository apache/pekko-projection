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

package org.apache.pekko.projection.state.scaladsl

import scala.concurrent.Future

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.persistence.query.Offset
import pekko.persistence.query.UpdatedDurableState
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.state.scaladsl._
import pekko.persistence.testkit.state.scaladsl.PersistenceTestKitDurableStateStore
import pekko.stream.testkit.scaladsl.TestSink
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object DurableStateSourceProviderSpec {
  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))
  final case class Record(id: Int, name: String)
}

class DurableStateSourceProviderSpec
    extends ScalaTestWithActorTestKit(DurableStateSourceProviderSpec.conf)
    with AnyWordSpecLike {
  "A DurableStateSourceProvider" must {
    import DurableStateSourceProviderSpec._

    "provide changes by tag" in {
      val record = Record(0, "Name-1")
      val tag = "tag-a"
      val recordChange = Record(0, "Name-2")

      val durableStateStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](PersistenceTestKitDurableStateStore.Identifier)
      implicit val ec = system.classicSystem.dispatcher
      val fut = Future.sequence(
        Vector(
          durableStateStore.upsertObject("persistent-id-1", 0L, record, tag),
          durableStateStore.upsertObject("persistent-id-2", 0L, record, "tag-b"),
          durableStateStore.upsertObject("persistent-id-1", 1L, recordChange, tag),
          durableStateStore.upsertObject("persistent-id-3", 0L, record, "tag-c")))
      whenReady(fut) { _ =>
        val sourceProvider =
          DurableStateSourceProvider.changesByTag[Record](system, PersistenceTestKitDurableStateStore.Identifier, tag)

        whenReady(sourceProvider.source(() => Future.successful[Option[Offset]](None))) { source =>
          val stateChange = source
            .collect { case u: UpdatedDurableState[Record] => u }
            .runWith(TestSink[UpdatedDurableState[Record]]())
            .request(1)
            .expectNext()

          stateChange.value should be(recordChange)
          stateChange.revision should be(1L)
        }
      }
    }
  }

}
