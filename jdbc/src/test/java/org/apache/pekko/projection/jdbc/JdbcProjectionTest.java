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

package org.apache.pekko.projection.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturingExtension;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.japi.pf.Match;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionContext;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.internal.JdbcOffsetStore;
import org.apache.pekko.projection.jdbc.internal.JdbcSettings;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import org.apache.pekko.projection.testkit.javadsl.ProjectionTestKit;
import org.apache.pekko.projection.testkit.javadsl.TestSourceProvider;
import org.apache.pekko.stream.javadsl.FlowWithContext;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import scala.Option;
import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.Future;

@ExtendWith(LogCapturingExtension.class)
public class JdbcProjectionTest {

  private static final Map<String, Object> configuration = new HashMap<>();

  static {
    configuration.put("pekko.projection.jdbc.dialect", "h2-dialect");
    configuration.put("pekko.projection.jdbc.offset-store.schema", "");
    configuration.put("pekko.projection.jdbc.offset-store.table", "pekko_projection_offset_store");
    configuration.put("pekko.projection.jdbc.use-dispatcher", "database.dispatcher");
    configuration.put("database.dispatcher.executor", "thread-pool-executor");
    configuration.put("database.dispatcher.throughput", 1);
    configuration.put("database.dispatcher.thread-pool-executor.fixed-pool-size", 5);
  }

  private static final Config config = ConfigFactory.parseMap(configuration);

  private static ActorTestKit testKit;
  private static JdbcSettings jdbcSettings;
  private static JdbcOffsetStore<PureJdbcSession> offsetStore;
  private static ProjectionTestKit projectionTestKit;

  static class PureJdbcSession implements JdbcSession {

    private final Connection connection;

    public PureJdbcSession() {
      try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        lookup.ensureInitialized(lookup.findClass("org.h2.Driver"));
        Connection c = DriverManager.getConnection("jdbc:h2:mem:test-java;DB_CLOSE_DELAY=-1");
        c.setAutoCommit(false);
        this.connection = c;
      } catch (ReflectiveOperationException | SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <Result> Result withConnection(Function<Connection, Result> func) throws Exception {
      return func.apply(connection);
    }

    @Override
    public void commit() throws SQLException {
      connection.commit();
    }

    @Override
    public void rollback() throws SQLException {
      connection.rollback();
    }

    @Override
    public void close() throws SQLException {
      connection.close();
    }
  }

  private static class JdbcSessionCreator implements Supplier<PureJdbcSession> {

    @Override
    public PureJdbcSession get() {
      return new PureJdbcSession();
    }
  }

  private static final JdbcSessionCreator jdbcSessionCreator = new JdbcSessionCreator();

  private static final scala.concurrent.duration.Duration awaitTimeout =
      scala.concurrent.duration.Duration.create(3, TimeUnit.SECONDS);

  @BeforeAll
  static void setup() throws Exception {
    testKit = ActorTestKit.create(config);
    jdbcSettings = JdbcSettings.apply(testKit.system());
    offsetStore = new JdbcOffsetStore<>(testKit.system(), jdbcSettings, jdbcSessionCreator::get);
    Await.result(offsetStore.createIfNotExists(), awaitTimeout);
    projectionTestKit = ProjectionTestKit.create(testKit.system());
  }

  @AfterAll
  static void teardown() {
    if (testKit != null) testKit.shutdownTestKit();
  }

  record Envelope(String id, long offset, String message) {}

  public static SourceProvider<Long, Envelope> sourceProvider(String entityId) {
    Source<Envelope, NotUsed> envelopes =
        Source.from(
            List.of(
                new Envelope(entityId, 1, "abc"),
                new Envelope(entityId, 2, "def"),
                new Envelope(entityId, 3, "ghi"),
                new Envelope(entityId, 4, "jkl"),
                new Envelope(entityId, 5, "mno"),
                new Envelope(entityId, 6, "pqr")));

    TestSourceProvider<Long, Envelope> sourceProvider =
        TestSourceProvider.create(envelopes, env -> env.offset())
            .withStartSourceFrom(
                (Long lastProcessedOffset, Long offset) -> offset <= lastProcessedOffset);

    return sourceProvider;
  }

  private ProjectionId genRandomProjectionId() {
    return ProjectionId.of(UUID.randomUUID().toString(), "00");
  }

  private void assertStoredOffset(ProjectionId projectionId, long expectedOffset) {
    testKit
        .createTestProbe()
        .awaitAssert(
            () -> {
              try {
                Future<Option<Long>> futOffset = offsetStore.readOffset(projectionId);
                long offset = Await.result(futOffset, awaitTimeout).get();
                assertEquals(expectedOffset, offset);
                return null;
              } catch (Exception e) {
                // from Await
                throw new RuntimeException(e);
              }
            });
  }

  private String failMessage(long offset) {
    return "fail on envelope with offset: [" + offset + "]";
  }

  private void expectNextUntilErrorMessage(TestSubscriber.Probe<Done> probe, String msg) {
    probe.request(1);
    PartialFunction<TestSubscriber.SubscriberEvent, Boolean> pf =
        Match.<TestSubscriber.SubscriberEvent, Boolean, TestSubscriber.OnError>match(
                TestSubscriber.OnError.class,
                err -> err.cause().getMessage().equals(msg),
                event -> true)
            .match(TestSubscriber.OnNext.class, event -> false)
            .build();
    if (!probe.expectEventPF(pf)) expectNextUntilErrorMessage(probe, msg);
  }

  private JdbcHandler<Envelope, PureJdbcSession> concatHandler(StringBuffer str) {
    return concatHandler(str, new CountDownLatch(0), __ -> false);
  }

  private JdbcHandler<Envelope, PureJdbcSession> concatHandler(
      StringBuffer buffer, CountDownLatch latch, Predicate<Long> failPredicate) {
    return JdbcHandler.fromFunction(
        (PureJdbcSession session, Envelope envelope) -> {
          if (failPredicate.test(envelope.offset())) {
            latch.countDown();
            throw new RuntimeException(failMessage(envelope.offset()));
          } else {
            buffer.append(envelope.message()).append("|");
            latch.countDown();
          }
        });
  }

  GroupedConcatHandler groupedConcatHandler(StringBuffer buffer, TestProbe<String> handlerProbe) {
    return new GroupedConcatHandler(buffer, handlerProbe);
  }

  static class GroupedConcatHandler extends JdbcHandler<List<Envelope>, PureJdbcSession> {

    public static final String handlerCalled = "called";
    private final StringBuffer buffer;
    private final TestProbe<String> handlerProbe;

    GroupedConcatHandler(StringBuffer buffer, TestProbe<String> handlerProbe) {
      this.buffer = buffer;
      this.handlerProbe = handlerProbe;
    }

    @Override
    public void process(PureJdbcSession session, List<Envelope> envelopes) {
      handlerProbe.ref().tell(GroupedConcatHandler.handlerCalled);
      for (Envelope envelope : envelopes) {
        buffer.append(envelope.message()).append("|");
      }
    }
  }

  @Test
  public void exactlyOnceShouldStoreOffset() {
    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    StringBuffer str = new StringBuffer();

    Projection<Envelope> projection =
        JdbcProjection.exactlyOnce(
            projectionId,
            sourceProvider(entityId),
            jdbcSessionCreator,
            () -> concatHandler(str),
            testKit.system());

    projectionTestKit.run(
        projection, () -> assertEquals("abc|def|ghi|jkl|mno|pqr|", str.toString()));

    assertStoredOffset(projectionId, 6L);
  }

  @Test
  public void exactlyOnceShouldRestartFromPreviousOffset() {
    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    StringBuffer str = new StringBuffer();
    CountDownLatch latch = new CountDownLatch(3);

    Projection<Envelope> projection =
        JdbcProjection.exactlyOnce(
            projectionId,
            sourceProvider(entityId),
            jdbcSessionCreator,
            // fail on fourth offset
            () -> concatHandler(str, latch, offset -> offset == 4),
            testKit.system());

    projectionTestKit.runWithTestSink(
        projection,
        (probe) -> {
          probe.request(3);
          probe.expectNextN(3);
          assertTrue(latch.await(3, TimeUnit.SECONDS));
          assertEquals("abc|def|ghi|", str.toString());
          expectNextUntilErrorMessage(probe, failMessage(4));
        });
  }

  @Test
  public void atLeastOnceShouldStoreOffset() {
    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    StringBuffer str = new StringBuffer();

    Projection<Envelope> projection =
        JdbcProjection.atLeastOnce(
                projectionId,
                sourceProvider(entityId),
                jdbcSessionCreator,
                () -> concatHandler(str),
                testKit.system())
            .withSaveOffset(1, Duration.ZERO);

    projectionTestKit.run(
        projection,
        () -> {
          assertEquals("abc|def|ghi|jkl|mno|pqr|", str.toString());
        });

    assertStoredOffset(projectionId, 6L);
  }

  @Test
  public void atLeastOnceShouldRestartFromPreviousOffset() {
    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    StringBuffer str = new StringBuffer();
    CountDownLatch latch = new CountDownLatch(3);

    Projection<Envelope> projection =
        JdbcProjection.atLeastOnce(
                projectionId,
                sourceProvider(entityId),
                jdbcSessionCreator,
                // fail on fourth offset
                () -> concatHandler(str, latch, offset -> offset == 4),
                testKit.system())
            .withSaveOffset(1, Duration.ZERO);

    projectionTestKit.runWithTestSink(
        projection,
        (probe) -> {
          /*
           * We only want to process 3 elements through the handler, but given buffering within the projections
           * at-least-once impl. we actually process +1 element than we requested with the TestSink().
           *
           * See https://github.com/akka/akka-projection/issues/462 for a possible solution.
           */
          probe.request(2);
          probe.expectNextN(2);
          assertTrue(latch.await(3, TimeUnit.SECONDS));
          assertEquals("abc|def|ghi|", str.toString());
          expectNextUntilErrorMessage(probe, failMessage(4));
        });

    assertStoredOffset(projectionId, 3L);

    // re-run projection without failing function
    Projection<Envelope> projection2 =
        JdbcProjection.atLeastOnce(
                projectionId,
                sourceProvider(entityId),
                jdbcSessionCreator,
                () -> concatHandler(str),
                testKit.system())
            .withSaveOffset(1, Duration.ZERO);

    projectionTestKit.run(
        projection2,
        () -> {
          assertEquals("abc|def|ghi|jkl|mno|pqr|", str.toString());
        });
  }

  @Test
  public void groupedShouldStoreOffset() {

    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    TestProbe<String> handlerProbe = testKit.createTestProbe("calls-to-handler");
    StringBuffer str = new StringBuffer();

    Projection<Envelope> projection =
        JdbcProjection.groupedWithin(
                projectionId,
                sourceProvider(entityId),
                jdbcSessionCreator,
                () -> groupedConcatHandler(str, handlerProbe),
                testKit.system())
            .withGroup(3, Duration.ofMinutes(1));

    projectionTestKit.run(
        projection, () -> assertEquals("abc|def|ghi|jkl|mno|pqr|", str.toString()));

    assertStoredOffset(projectionId, 6L);

    // handler probe is called twice
    handlerProbe.expectMessage(GroupedConcatHandler.handlerCalled);
    handlerProbe.expectMessage(GroupedConcatHandler.handlerCalled);
  }

  @Test
  public void atLeastOnceFlowShouldStoreOffset() {

    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = genRandomProjectionId();

    StringBuffer str = new StringBuffer();

    FlowWithContext<Envelope, ProjectionContext, Done, ProjectionContext, NotUsed> flow =
        FlowWithContext.<Envelope, ProjectionContext>create()
            .map(
                envelope -> {
                  str.append(envelope.message()).append("|");
                  return Done.getInstance();
                });

    Projection<Envelope> projection =
        JdbcProjection.atLeastOnceFlow(
                projectionId, sourceProvider(entityId), jdbcSessionCreator, flow, testKit.system())
            .withSaveOffset(1, Duration.ofMinutes(1));

    projectionTestKit.run(
        projection, () -> assertEquals("abc|def|ghi|jkl|mno|pqr|", str.toString()));

    assertStoredOffset(projectionId, 6L);
  }
}
