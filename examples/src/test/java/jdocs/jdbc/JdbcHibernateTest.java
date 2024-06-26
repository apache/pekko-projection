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

package jdocs.jdbc;

import static org.junit.Assert.assertEquals;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.internal.JdbcOffsetStore;
import org.apache.pekko.projection.jdbc.internal.JdbcSettings;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import org.apache.pekko.projection.testkit.javadsl.ProjectionTestKit;
import org.apache.pekko.projection.testkit.javadsl.TestSourceProvider;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;

public class JdbcHibernateTest extends JUnitSuite {
  private static final Map<String, Object> configuration = new HashMap<>();

  static {
    configuration.put("pekko.projection.jdbc.dialect", "h2-dialect");
    configuration.put("pekko.projection.jdbc.offset-store.schema", "");
    configuration.put("pekko.projection.jdbc.offset-store.table", "PEKKO_PROJECTION_OFFSET_STORE");
    configuration.put(
        "pekko.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size", 5);
  }

  private static final Config config = ConfigFactory.parseMap(configuration);
  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource(config);
  @Rule public final LogCapturing logCapturing = new LogCapturing();

  private final ProjectionTestKit projectionTestKit = ProjectionTestKit.create(testKit.system());

  static class Envelope {
    final String id;
    final long offset;
    final String message;

    Envelope(String id, long offset, String message) {
      this.id = id;
      this.offset = offset;
      this.message = message;
    }
  }

  private static final HibernateSessionFactory sessionProvider = new HibernateSessionFactory();

  private static final JdbcSettings jdbcSettings = JdbcSettings.apply(testKit.system());
  private static final JdbcOffsetStore<HibernateJdbcSession> offsetStore =
      new JdbcOffsetStore<>(testKit.system(), jdbcSettings, () -> sessionProvider.newInstance());

  private static final scala.concurrent.duration.Duration awaitTimeout =
      scala.concurrent.duration.Duration.create(3, TimeUnit.SECONDS);

  @BeforeClass
  public static void beforeAll() throws Exception {
    Await.result(offsetStore.createIfNotExists(), awaitTimeout);
  }

  public static SourceProvider<Long, Envelope> sourceProvider(String entityId) {
    Source<Envelope, NotUsed> envelopes =
        Source.from(
            Arrays.asList(
                new Envelope(entityId, 1, "abc"),
                new Envelope(entityId, 2, "def"),
                new Envelope(entityId, 3, "ghi"),
                new Envelope(entityId, 4, "jkl"),
                new Envelope(entityId, 5, "mno"),
                new Envelope(entityId, 6, "pqr")));

    TestSourceProvider<Long, Envelope> sourceProvider =
        TestSourceProvider.create(envelopes, env -> env.offset)
            .withStartSourceFrom(
                (Long lastProcessedOffset, Long offset) -> offset <= lastProcessedOffset);

    return sourceProvider;
  }

  private JdbcHandler<Envelope, HibernateJdbcSession> concatHandler(StringBuffer buffer) {
    return new JdbcHandler<Envelope, HibernateJdbcSession>() {
      @Override
      public void process(HibernateJdbcSession session, Envelope envelope) {
        buffer.append(envelope.message).append("|");
      }
    };
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

  @Test
  public void testWeCanEffectivelyIntegrateWithHibernate() {

    String entityId = UUID.randomUUID().toString();
    ProjectionId projectionId = ProjectionId.of(UUID.randomUUID().toString(), "00");
    StringBuffer buffer = new StringBuffer();

    Projection<Envelope> projection =
        JdbcProjection.exactlyOnce(
            projectionId,
            sourceProvider(entityId),
            () -> sessionProvider.newInstance(),
            () -> concatHandler(buffer),
            testKit.system());

    projectionTestKit.run(
        projection, () -> assertEquals("abc|def|ghi|jkl|mno|pqr|", buffer.toString()));

    assertStoredOffset(projectionId, 6L);
  }
}
