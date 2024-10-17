package org.apache.pekko.projection.eventsourced.scaldsl

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.LogCapturing
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.persistence.Persistence
import org.apache.pekko.persistence.query.NoOffset
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.freespec.AnyFreeSpecLike

import scala.concurrent.Future

object EventSourcedProviderSpec {
  private val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
      """))

  private case class Command(event: String, ack: ActorRef[Done])
  private case class State()

  private def testBehaviour(persistenceId: String, tag: String, maybeJournal: Option[String] = None) = {
    val behavior = EventSourcedBehavior[Command, String, State](
      PersistenceId.ofUniqueId(persistenceId),
      State(),
      (_, command) =>
        Effect.persist(command.event).thenRun { _ =>
          command.ack ! Done
        },
      (state, _) => state)
      .withTagger(_ => Set(tag))
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
}

class EventSourcedProviderSpec
    extends ScalaTestWithActorTestKit(EventSourcedProviderSpec.config)
    with LogCapturing
    with AnyFreeSpecLike {
  import EventSourcedProviderSpec._

  implicit private val classic: pekko.actor.ActorSystem = system.classicSystem

  private def setup(pid: String, event: String, tag: String, maybeJournal: Option[String] = None) = {
    val probe = createTestProbe[Done]()
    val ref = spawn(testBehaviour(pid, tag, maybeJournal))
    ref ! Command(event, probe.ref)
    probe.expectMessage(Done)
  }

  private def assertTag(tag: String, expected: String, maybeJournal: Option[String] = None) = {
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
      .request(1)
      .expectNext(expected)
      .request(1)
      .expectNoMessage()
  }

  "Should provide different events" - {

    "for different tags" in {
      val pid1 = "a-id-1"
      val pid2 = "a-id-2"
      val event1 = "a-event-1"
      val event2 = "a-event-2"
      val tag1 = "a-tag-1"
      val tag2 = "a-tag-2"

      setup(pid1, event1, tag1)
      setup(pid2, event2, tag2)

      assertTag(tag1, event1)
      assertTag(tag2, event2)
    }

    "for different journals" in {
      val pid1 = "b-id-1"
      val event1 = "b-event-1"
      val event2 = "b-event-2"
      val tag1 = "b-tag-1"
      val journal1 = "b-journal-1"
      val journal2 = "b-journal-2"

      setup(pid1, event1, tag1, Some(journal1))
      setup(pid1, event2, tag1, Some(journal2))

      assertTag(tag1, event1, Some(journal1))
      assertTag(tag1, event2, Some(journal2))
    }
  }
}
