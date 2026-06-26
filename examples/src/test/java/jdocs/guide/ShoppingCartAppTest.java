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

// #testKitSpec
package jdocs.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.LoggingTestKit;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import org.apache.pekko.projection.javadsl.Handler;
import org.apache.pekko.projection.javadsl.SourceProvider;
// #testKitImports
import org.apache.pekko.projection.testkit.javadsl.ProjectionTestKit;
import org.apache.pekko.projection.testkit.javadsl.TestProjection;
import org.apache.pekko.projection.testkit.javadsl.TestSourceProvider;
// #testKitImports
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ShoppingCartAppTest {

  private static ActorTestKit testKit;
  private static ProjectionTestKit projectionTestKit;

  @BeforeAll
  static void setup() {
    testKit = ActorTestKit.create();
    projectionTestKit = ProjectionTestKit.create(testKit.system());
  }

  @AfterAll
  static void teardown() {
    testKit.shutdownTestKit();
  }

  EventEnvelope<ShoppingCartEvents.Event> createEnvelope(
      ShoppingCartEvents.Event event, Long seqNo, Long timestamp) {
    return EventEnvelope.create(Offset.sequence(seqNo), "persistenceId", seqNo, event, timestamp);
  }

  @Test
  public void projectionHandlerShouldProcessItemEventsCorrectly() {
    MockItemPopularityRepository repo = new MockItemPopularityRepository();
    Handler<EventEnvelope<ShoppingCartEvents.Event>> handler =
        new ItemPopularityProjectionHandler("tag", testKit.system(), repo);

    Source<EventEnvelope<ShoppingCartEvents.Event>, NotUsed> events =
        Source.from(
            List.of(
                createEnvelope(
                    new ShoppingCartEvents.ItemAdded("a7098", "bowling shoes", 1), 0L, 0L),
                createEnvelope(
                    new ShoppingCartEvents.ItemQuantityAdjusted("a7098", "bowling shoes", 2, 1),
                    1L,
                    0L),
                createEnvelope(
                    new ShoppingCartEvents.CheckedOut(
                        "a7098", Instant.parse("2020-01-01T12:00:00.00Z")),
                    2L,
                    0L),
                createEnvelope(
                    new ShoppingCartEvents.ItemAdded("0d12d", "pekko t-shirt", 1), 3L, 0L),
                createEnvelope(new ShoppingCartEvents.ItemAdded("0d12d", "skis", 1), 4L, 0L),
                createEnvelope(new ShoppingCartEvents.ItemRemoved("0d12d", "skis", 1), 5L, 0L),
                createEnvelope(
                    new ShoppingCartEvents.CheckedOut(
                        "0d12d", Instant.parse("2020-01-01T12:05:00.00Z")),
                    6L,
                    0L)));

    ProjectionId projectionId = ProjectionId.of("name", "key");
    SourceProvider<Offset, EventEnvelope<ShoppingCartEvents.Event>> sourceProvider =
        TestSourceProvider.create(events, env -> env.offset());
    TestProjection<Offset, EventEnvelope<ShoppingCartEvents.Event>> projection =
        TestProjection.create(projectionId, sourceProvider, () -> handler);

    projectionTestKit.run(
        projection,
        () -> {
          assertEquals(3, repo.counts.size());
          assertEquals(Long.valueOf(2L), repo.counts.get("bowling shoes"));
          assertEquals(Long.valueOf(1L), repo.counts.get("pekko t-shirt"));
          assertEquals(Long.valueOf(0L), repo.counts.get("skis"));
        });
  }

  @Test
  public void projectionHandlerShouldLogItemPopularityEvery10Events() {
    long eventsNum = 10L;
    MockItemPopularityRepository repo = new MockItemPopularityRepository();
    Handler<EventEnvelope<ShoppingCartEvents.Event>> handler =
        new ItemPopularityProjectionHandler("tag", testKit.system(), repo);

    Source<EventEnvelope<ShoppingCartEvents.Event>, NotUsed> events =
        Source.fromJavaStream(
            () ->
                IntStream.range(0, (int) eventsNum)
                    .boxed()
                    .map(
                        i ->
                            createEnvelope(
                                new ShoppingCartEvents.ItemAdded("a7098", "bowling shoes", 1),
                                Long.valueOf(i),
                                0L)));

    ProjectionId projectionId = ProjectionId.of("name", "key");
    SourceProvider<Offset, EventEnvelope<ShoppingCartEvents.Event>> sourceProvider =
        TestSourceProvider.create(events, env -> env.offset());
    TestProjection<Offset, EventEnvelope<ShoppingCartEvents.Event>> projection =
        TestProjection.create(projectionId, sourceProvider, () -> handler);

    LoggingTestKit.info(
            "ItemPopularityProjectionHandler(tag) item popularity for 'bowling shoes': [10]")
        .expect(
            testKit.system(),
            () -> {
              projectionTestKit.runWithTestSink(
                  projection,
                  testSink -> {
                    testSink.request(eventsNum);
                    testSink.expectNextN(eventsNum);
                  });
              return null; // FIXME: why is a return statement required?
            });
  }

  static class MockItemPopularityRepository implements ItemPopularityProjectionRepository {
    public Map<String, Long> counts = new HashMap<String, Long>();

    @Override
    public CompletionStage<Done> update(String itemId, int delta) {
      counts.put(itemId, counts.getOrDefault(itemId, 0L) + delta);
      return CompletableFuture.completedFuture(Done.getInstance());
    }

    @Override
    public CompletionStage<Optional<Long>> getItem(String itemId) {
      if (counts.containsKey(itemId))
        return CompletableFuture.completedFuture(Optional.of(counts.get(itemId)));

      return CompletableFuture.completedFuture(Optional.empty());
    }
  }
}
// #testKitSpec
