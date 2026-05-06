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
import scala.concurrent.ExecutionContext

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.persistence.Persistence
import pekko.persistence.query.Offset
import pekko.persistence.query.UpdatedDurableState
import pekko.persistence.query.scaladsl.DurableStateStoreQuery
import pekko.persistence.query.typed.scaladsl.DurableStateStoreBySliceQuery
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.state.scaladsl._
import pekko.persistence.testkit.state.scaladsl.PersistenceTestKitDurableStateStore
import pekko.stream.testkit.scaladsl.TestSink
import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object DurableStateSourceProviderSpec {
  val customPluginId = "custom-durable-state-store"

  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    $customPluginId {
      class = "org.apache.pekko.persistence.testkit.state.PersistenceTestKitDurableStateStoreProvider"
    }
    """))
  final case class Record(id: Int, name: String)
}

class DurableStateSourceProviderSpec
    extends ScalaTestWithActorTestKit(DurableStateSourceProviderSpec.conf)
    with AnyWordSpecLike {

  private lazy val persistence = Persistence(system)
  private lazy val numberOfSlices = persistence.numberOfSlices
  implicit val ec: ExecutionContext = system.executionContext

  "A DurableStateSourceProvider" must {
    import DurableStateSourceProviderSpec._

    "provide changes by tag" in {
      val record = Record(0, "Name-1")
      val tag = "tag-a"
      val recordChange = Record(0, "Name-2")

      val durableStateStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](PersistenceTestKitDurableStateStore.Identifier)
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

    "provide changes by tag using a direct query instance" in {
      val record = Record(0, "Name-1")
      val tag = "tag-direct"
      val recordChange = Record(0, "Name-2")

      val durableStateStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](PersistenceTestKitDurableStateStore.Identifier)
      val fut = Future.sequence(
        Vector(
          durableStateStore.upsertObject("persistent-id-direct-1", 0L, record, tag),
          durableStateStore.upsertObject("persistent-id-direct-2", 0L, record, "tag-other"),
          durableStateStore.upsertObject("persistent-id-direct-1", 1L, recordChange, tag)))
      whenReady(fut) { _ =>
        val queryInstance =
          DurableStateStoreRegistry(system)
            .durableStateStoreFor[DurableStateStoreQuery[Record]](PersistenceTestKitDurableStateStore.Identifier)
        val sourceProvider = DurableStateSourceProvider.changesByTag[Record](system, queryInstance, tag)

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

    "provide changes by slices" in {
      val entityType = "SliceEntity"
      val persistenceId = s"$entityType|slice-id-1"
      val record = Record(1, "slice-record-1")
      val recordChange = Record(1, "slice-record-2")
      val slice = persistence.sliceForPersistenceId(persistenceId)

      val durableStateStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](PersistenceTestKitDurableStateStore.Identifier)
      val fut = Future.sequence(
        Vector(
          durableStateStore.upsertObject(persistenceId, 0L, record, ""),
          durableStateStore.upsertObject(persistenceId, 1L, recordChange, "")))
      whenReady(fut) { _ =>
        val sourceProvider = DurableStateSourceProvider.changesBySlices[Record](
          system,
          PersistenceTestKitDurableStateStore.Identifier,
          entityType,
          slice,
          slice)

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

    "provide changes by slices using a direct query instance" in {
      val entityType = "SliceEntityDirect"
      val persistenceId = s"$entityType|slice-direct-id-1"
      val record = Record(2, "slice-direct-record-1")
      val recordChange = Record(2, "slice-direct-record-2")
      val slice = persistence.sliceForPersistenceId(persistenceId)

      val durableStateStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](PersistenceTestKitDurableStateStore.Identifier)
      val fut = Future.sequence(
        Vector(
          durableStateStore.upsertObject(persistenceId, 0L, record, ""),
          durableStateStore.upsertObject(persistenceId, 1L, recordChange, "")))
      whenReady(fut) { _ =>
        val queryInstance =
          DurableStateStoreRegistry(system)
            .durableStateStoreFor[DurableStateStoreBySliceQuery[Record]](PersistenceTestKitDurableStateStore.Identifier)
        val sourceProvider =
          DurableStateSourceProvider.changesBySlices[Record](system, queryInstance, entityType, slice, slice)

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

    "return slice for persistence id" in {
      val entityType = "SliceQueryEntity"
      val persistenceId = s"$entityType|query-id-1"
      val slice = DurableStateSourceProvider.sliceForPersistenceId(
        system,
        PersistenceTestKitDurableStateStore.Identifier,
        persistenceId)
      slice shouldBe persistence.sliceForPersistenceId(persistenceId)
    }

    "return slice ranges" in {
      val sliceRanges = DurableStateSourceProvider.sliceRanges(
        system,
        PersistenceTestKitDurableStateStore.Identifier,
        1)
      sliceRanges shouldBe Seq(0 until numberOfSlices)
    }

    // Tests using a non-default durable state store plugin configuration
    "provide changes by tag using a non-default plugin config" in {
      val record = Record(0, "Name-custom-1")
      val tag = "tag-custom-plugin"
      val recordChange = Record(0, "Name-custom-2")

      val customStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](customPluginId)
      val fut = Future.sequence(
        Vector(
          customStore.upsertObject("custom-persistent-id-1", 0L, record, tag),
          customStore.upsertObject("custom-persistent-id-2", 0L, record, "tag-other"),
          customStore.upsertObject("custom-persistent-id-1", 1L, recordChange, tag)))
      whenReady(fut) { _ =>
        val sourceProvider = DurableStateSourceProvider.changesByTag[Record](system, customPluginId, tag)

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

    "provide changes by slices using a non-default plugin config" in {
      val entityType = "CustomPluginEntity"
      val persistenceId = s"$entityType|custom-plugin-slice-id-1"
      val record = Record(3, "custom-slice-record-1")
      val recordChange = Record(3, "custom-slice-record-2")
      val slice = persistence.sliceForPersistenceId(persistenceId)

      val customStore: DurableStateUpdateStore[Record] =
        DurableStateStoreRegistry(system)
          .durableStateStoreFor[DurableStateUpdateStore[Record]](customPluginId)
      val fut = Future.sequence(
        Vector(
          customStore.upsertObject(persistenceId, 0L, record, ""),
          customStore.upsertObject(persistenceId, 1L, recordChange, "")))
      whenReady(fut) { _ =>
        val sourceProvider = DurableStateSourceProvider.changesBySlices[Record](
          system,
          customPluginId,
          entityType,
          slice,
          slice)

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

    // Negative tests
    "return None from getObject for a non-existent persistenceId" in {
      val entityType = "NonExistentEntity"
      val persistenceId = s"$entityType|does-not-exist"
      val slice = persistence.sliceForPersistenceId(persistenceId)

      val sourceProvider = DurableStateSourceProvider
        .changesBySlices[Record](system, PersistenceTestKitDurableStateStore.Identifier, entityType, slice, slice)
      val result = sourceProvider.asInstanceOf[DurableStateStore[Record]].getObject(persistenceId)
      whenReady(result) { objectResult =>
        objectResult.value shouldBe None
        objectResult.revision shouldBe 0L
      }
    }

    "return an empty source for changesByTag when no records match the tag" in {
      val emptyTag = "tag-with-no-records"

      val sourceProvider =
        DurableStateSourceProvider.changesByTag[Record](system, PersistenceTestKitDurableStateStore.Identifier, emptyTag)

      whenReady(sourceProvider.source(() => Future.successful[Option[Offset]](None))) { source =>
        source
          .runWith(TestSink[pekko.persistence.query.DurableStateChange[Record]]())
          .request(1)
          .expectNoMessage()
      }
    }
  }

}
