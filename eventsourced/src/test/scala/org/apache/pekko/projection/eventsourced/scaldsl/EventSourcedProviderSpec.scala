package org.apache.pekko.projection.eventsourced.scaldsl

import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.persistence.Persistence
import pekko.persistence.query.NoOffset
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.freespec.AnyFreeSpecLike

object EventSourcedProviderSpec {
  private val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.persistence.testkit.events.serialize = off
      """))

  private case class Command(event: String, ack: ActorRef[Done])
  private case class State()

  private def testBehaviour(persistenceId: String, tags: Set[String], maybeJournal: Option[String] = None) = {
    val behavior = EventSourcedBehavior[Command, String, State](
      PersistenceId.ofUniqueId(persistenceId),
      State(),
      (_, command) =>
        Effect.persist(command.event).thenRun { _ =>
          command.ack ! Done
        },
      (state, _) => state)
      .withTagger(_ => tags)
    maybeJournal.fold(
      behavior
    )(journal =>
      behavior
        .withJournalPluginId(s"$journal.journal")
        .withJournalPluginConfig(Some(journalConfig(journal))))
  }

  private def journalConfig(journal: String) = {
    ConfigFactory.parseString(s"""
      $journal {
        journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
        query = $${pekko.persistence.testkit.query}
      }
    """).withFallback(ConfigFactory.load()).resolve()
  }

  private val entityType = "Test"
  private def makeFullPersistenceId(persistenceId: String) = {
    s"$entityType|$persistenceId"
  }
}

class EventSourcedProviderSpec
    extends ScalaTestWithActorTestKit(EventSourcedProviderSpec.config)
    with LogCapturing
    with AnyFreeSpecLike {
  import EventSourcedProviderSpec._

  implicit private val classic: pekko.actor.ActorSystem = system.classicSystem
  private lazy val persistence = Persistence(system)
  private lazy val numberOfSlices = persistence.numberOfSlices

  private def setup(persistenceId: String, tags: Set[String] = Set.empty, maybeJournal: Option[String] = None) = {
    val probe = createTestProbe[Done]()
    val ref = spawn(testBehaviour(persistenceId, tags, maybeJournal))
    def makeEvent(event: String) = {
      Seq(Some(s"$persistenceId-$event"), maybeJournal).flatten.mkString("-")
    }
    ref ! Command(makeEvent("event-1"), probe.ref)
    ref ! Command(makeEvent("event-2"), probe.ref)
    ref ! Command(makeEvent("event-3"), probe.ref)
    probe.expectMessage(Done)
  }

  private def assertTag(tag: String, expectedEvents: Seq[String], maybeJournal: Option[String] = None) = {
    maybeJournal
      .fold(
        EventSourcedProvider
          .eventsByTag[String](
            system,
            PersistenceTestKitReadJournal.Identifier,
            tag
          )
      )(journal =>
        EventSourcedProvider
          .eventsByTag[String](
            system,
            s"$journal.query",
            journalConfig(journal),
            tag
          ))
      .source(() => Future.successful(Some(NoOffset)))
      .futureValue
      .map(_.event)
      .runWith(TestSink.probe)
      .request(expectedEvents.size)
      .expectNextN(expectedEvents)
      .request(1)
      .expectNoMessage()
  }

  private def assertSlices(minSlice: Int, maxSlice: Int, expectedEvents: Seq[String],
      maybeJournal: Option[String] = None) = {
    maybeJournal
      .fold(
        EventSourcedProvider
          .eventsBySlices[String](
            system,
            PersistenceTestKitReadJournal.Identifier,
            entityType,
            minSlice,
            maxSlice
          )
      )(journal =>
        EventSourcedProvider
          .eventsBySlices[String](
            system,
            s"$journal.query",
            journalConfig(journal),
            entityType,
            minSlice,
            maxSlice
          ))
      .source(() => Future.successful(Some(NoOffset)))
      .futureValue
      .map(_.event)
      .runWith(TestSink.probe)
      .request(expectedEvents.size)
      .expectNextN(expectedEvents)
      .request(1)
      .expectNoMessage()
  }

  "Should provide different events" - {
    "by tags" - {
      "for different tags" in {
        val persistenceId1 = "a-id-1"
        val persistenceId2 = "a-id-2"
        val tag1 = "a-tag-1"
        val tag2 = "a-tag-2"

        setup(persistenceId1, Set(tag1))
        setup(persistenceId2, Set(tag2))

        assertTag(tag1, Seq(s"$persistenceId1-event-1", s"$persistenceId1-event-2", s"$persistenceId1-event-3"))
        assertTag(tag2, Seq(s"$persistenceId2-event-1", s"$persistenceId2-event-2", s"$persistenceId2-event-3"))
      }

      "for different journals" in {
        val persistenceId1 = "b-id-1"
        val tag1 = "b-tag-1"
        val journal1 = "b-journal-1"
        val journal2 = "b-journal-2"

        setup(persistenceId1, Set(tag1), Some(journal1))
        setup(persistenceId1, Set(tag1), Some(journal2))

        val expectedEvents = Seq(s"$persistenceId1-event-1", s"$persistenceId1-event-2", s"$persistenceId1-event-3")
        assertTag(tag1, expectedEvents.map(_ + s"-$journal1"), Some(journal1))
        assertTag(tag1, expectedEvents.map(_ + s"-$journal2"), Some(journal2))
      }
    }

    "by slices" - {
      "for different slices" in {
        val persistenceId1 = makeFullPersistenceId("c-id-1")
        val persistenceId2 = makeFullPersistenceId("c-id-2")

        val slice1 = persistence.sliceForPersistenceId(persistenceId1)
        val slice2 = persistence.sliceForPersistenceId(persistenceId2)
        slice1 should not be slice2

        setup(persistenceId1)
        setup(persistenceId2)

        val expectedEvents1 = Seq(s"$persistenceId1-event-1", s"$persistenceId1-event-2", s"$persistenceId1-event-3")
        val expectedEvents2 = Seq(s"$persistenceId2-event-1", s"$persistenceId2-event-2", s"$persistenceId2-event-3")
        assertSlices(0, numberOfSlices - 1, expectedEvents1 ++ expectedEvents2)
        assertSlices(slice1, slice1, expectedEvents1)
        assertSlices(slice2, slice2, expectedEvents2)
      }

      "for different journals" in {
        val persistenceId1 = makeFullPersistenceId("d-id-1")
        val journal1 = "d-journal-1"
        val journal2 = "d-journal-2"

        setup(persistenceId1, maybeJournal = Some(journal1))
        setup(persistenceId1, maybeJournal = Some(journal2))

        val expectedEvents = Seq(s"$persistenceId1-event-1", s"$persistenceId1-event-2", s"$persistenceId1-event-3")
        assertSlices(0, numberOfSlices - 1, expectedEvents.map(_ + s"-$journal1"), maybeJournal = Some(journal1))
        assertSlices(0, numberOfSlices - 1, expectedEvents.map(_ + s"-$journal2"), maybeJournal = Some(journal2))
      }
    }
  }
}
