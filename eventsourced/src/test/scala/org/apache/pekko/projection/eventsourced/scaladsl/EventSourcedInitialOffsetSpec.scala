/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.projection.eventsourced.scaladsl

import java.util.Collections
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

import scala.concurrent.Future
import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.japi.Pair
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.Sequence
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.query.typed.scaladsl.EventsBySliceQuery
import pekko.projection.eventsourced.javadsl.{ EventSourcedProvider => JavaEventSourcedProvider }
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import org.scalatest.freespec.AnyFreeSpecLike

class EventSourcedInitialOffsetSpec extends ScalaTestWithActorTestKit with AnyFreeSpecLike {
  implicit private val classic: pekko.actor.ActorSystem = system.classicSystem
  private val entityType = "Test"

  "choose the starting offset for a slice query" - {
    val bootstrap = Offset.sequence(42L)
    val checkpoint = Offset.sequence(51L)

    def recordingQuery(observed: ConcurrentLinkedQueue[Offset]): EventsBySliceQuery =
      new EventsBySliceQuery {
        override def eventsBySlices[Event](
            entityType: String,
            minSlice: Int,
            maxSlice: Int,
            offset: Offset): Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed] = {
          observed.add(offset)
          Source.empty
        }

        override def sliceForPersistenceId(persistenceId: String): Int = 0

        override def sliceRanges(numberOfRanges: Int): Seq[Range] = Nil
      }

    def recordingJavaQuery(
        observed: ConcurrentLinkedQueue[Offset]): pekko.persistence.query.typed.javadsl.EventsBySliceQuery =
      new pekko.persistence.query.typed.javadsl.EventsBySliceQuery {
        override def eventsBySlices[Event](
            entityType: String,
            minSlice: Int,
            maxSlice: Int,
            offset: Offset)
            : pekko.stream.javadsl.Source[pekko.persistence.query.typed.EventEnvelope[Event], NotUsed] = {
          observed.add(offset)
          pekko.stream.javadsl.Source.empty()
        }

        override def sliceForPersistenceId(persistenceId: String): Int = 0

        override def sliceRanges(numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] =
          Collections.emptyList()
      }

    "use the Scala supplier only while the checkpoint is absent" in {
      val observed = new ConcurrentLinkedQueue[Offset]
      val invocations = new AtomicInteger
      val query = recordingQuery(observed)
      val provider = EventSourcedProvider.eventsBySlices[String](system, query, entityType, 0, 0,
        () => {
          invocations.incrementAndGet()
          Future.successful(bootstrap)
        })

      invocations.get() shouldBe 0
      provider.source(() => Future.successful(None)).futureValue
      observed.remove() shouldBe bootstrap
      provider.source(() => Future.successful(Some(checkpoint))).futureValue
      observed.remove() shouldBe checkpoint
      provider.source(() => Future.successful(Some(NoOffset))).futureValue
      observed.remove() shouldBe NoOffset
      invocations.get() shouldBe 1
      provider.source(() => Future.successful(None)).futureValue
      observed.remove() shouldBe bootstrap
      invocations.get() shouldBe 2

      val ordinary = EventSourcedProvider.eventsBySlices[String](system, query, entityType, 0, 0)
      ordinary.source(() => Future.successful(None)).futureValue
      observed.remove() shouldBe NoOffset
    }

    "fail the Scala source without querying on a failed offset store or supplier" in {
      val failedStore = new IllegalStateException("offset store unavailable")
      val failedSupplier = new IllegalArgumentException("initial offset unavailable")
      val observed = new ConcurrentLinkedQueue[Offset]
      val invocations = new AtomicInteger
      val provider = EventSourcedProvider.eventsBySlices[String](
        system,
        recordingQuery(observed),
        entityType,
        0,
        0,
        () => {
          invocations.incrementAndGet()
          Future.failed(failedSupplier)
        })

      provider.source(() => Future.failed(failedStore)).failed.futureValue shouldBe failedStore
      invocations.get() shouldBe 0
      provider.source(() => Future.successful(None)).failed.futureValue shouldBe failedSupplier
      invocations.get() shouldBe 1
      observed.isEmpty shouldBe true
    }

    "preserve Java checkpoint precedence and propagate a supplier failure" in {
      val failure = new IllegalArgumentException("initial offset unavailable")
      val observed = new ConcurrentLinkedQueue[Offset]
      val invocations = new AtomicInteger
      val start = new Supplier[CompletableFuture[Offset]] {
        override def get(): CompletableFuture[Offset] =
          if (invocations.getAndIncrement() == 0) CompletableFuture.completedFuture(bootstrap)
          else CompletableFuture.failedFuture[Offset](failure)
      }
      val provider = JavaEventSourcedProvider.eventsBySlices[String](
        system, recordingJavaQuery(observed), entityType, 0, 0, start)

      invocations.get() shouldBe 0
      provider.source(() => CompletableFuture.completedFuture(Optional.empty[Offset]())).asScala.futureValue
      observed.remove() shouldBe bootstrap
      provider.source(() => CompletableFuture.completedFuture(Optional.of(checkpoint))).asScala.futureValue
      observed.remove() shouldBe checkpoint
      invocations.get() shouldBe 1
      provider
        .source(() => CompletableFuture.completedFuture(Optional.empty[Offset]()))
        .asScala
        .failed
        .futureValue shouldBe failure
      invocations.get() shouldBe 2
      observed.isEmpty shouldBe true
    }

    "fail the Java source for a supplier that throws before returning a CompletionStage" in {
      val failure = new IllegalArgumentException("initial offset unavailable")
      val observed = new ConcurrentLinkedQueue[Offset]
      val start: Supplier[CompletionStage[Offset]] = () => throw failure
      val provider = JavaEventSourcedProvider.eventsBySlices[String](
        system, recordingJavaQuery(observed), entityType, 0, 0, start)

      provider
        .source(() => CompletableFuture.completedFuture(Optional.empty[Offset]()))
        .asScala
        .failed
        .futureValue shouldBe failure
      observed.isEmpty shouldBe true
    }
    "continue after the first checkpoint without reapplying the initial boundary" in {
      def envelope(position: Long, id: String, sequenceNr: Long): EventEnvelope[String] =
        EventEnvelope(Offset.sequence(position), id, sequenceNr, s"$id:$sequenceNr", position, entityType, 0)

      val storedEvents = List(
        envelope(10L, "Test|before", 1L),
        envelope(20L, "Test|current", 1L),
        envelope(30L, "Test|current", 2L),
        envelope(40L, "Test|other", 1L))
      val observed = new ConcurrentLinkedQueue[Offset]
      val invocations = new AtomicInteger
      val query = new EventsBySliceQuery {
        override def eventsBySlices[Event](
            entityType: String,
            minSlice: Int,
            maxSlice: Int,
            offset: Offset): Source[EventEnvelope[Event], NotUsed] = {
          observed.add(offset)
          // The fixture only calls this stub as a String query.
          val position = offset.asInstanceOf[Sequence].value
          Source(storedEvents.filter(_.offset.asInstanceOf[Sequence].value > position))
            .map(_.asInstanceOf[EventEnvelope[Event]])
        }

        override def sliceForPersistenceId(persistenceId: String): Int = 0

        override def sliceRanges(numberOfRanges: Int): Seq[Range] = Nil
      }
      val initial = Offset.sequence(15L)
      val provider = EventSourcedProvider.eventsBySlices[String](system, query, entityType, 0, 0,
        () => {
          invocations.incrementAndGet()
          Future.successful(initial)
        })

      val first = provider.source(() => Future.successful(None)).futureValue.runWith(Sink.head).futureValue
      first.persistenceId shouldBe "Test|current"
      first.sequenceNr shouldBe 1L
      observed.remove() shouldBe initial

      val afterRestart = provider
        .source(() => Future.successful(Some(first.offset)))
        .futureValue
        .runWith(Sink.seq)
        .futureValue
      afterRestart.map(event => event.persistenceId -> event.sequenceNr) shouldBe
      Seq("Test|current" -> 2L, "Test|other" -> 1L)
      observed.remove() shouldBe first.offset
      invocations.get() shouldBe 1
    }
  }
}
