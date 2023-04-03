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

package jdocs.cassandra;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.cassandra.ContainerSessionProvider;
import org.apache.pekko.projection.cassandra.javadsl.CassandraProjection;
import org.apache.pekko.projection.testkit.javadsl.ProjectionTestKit;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSession;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSessionRegistry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.concurrent.Await;

import static jdocs.cassandra.WordCountDocExample.*;
import static jdocs.cassandra.WordCountDocExample.IllustrateStatefulHandlerLoadingInitialState.WordCountHandler;
import static jdocs.cassandra.WordCountDocExample.IllstrateActorLoadingInitialState.WordCountActorHandler;
import static jdocs.cassandra.WordCountDocExample.IllstrateActorLoadingInitialState.WordCountProcessor;
import static org.junit.Assert.assertEquals;

public class WordCountDocExampleTest extends JUnitSuite {
  @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  private static CassandraSession session;
  private static CassandraWordCountRepository repository;

  @BeforeClass
  public static void beforeAll() throws Exception {
    Await.result(
        ContainerSessionProvider.started(),
        scala.concurrent.duration.Duration.create(30, TimeUnit.SECONDS));

    session =
        CassandraSessionRegistry.get(testKit.system())
            .sessionFor("pekko.projection.cassandra.session-config");
    CassandraProjection.createTablesIfNotExists(testKit.system())
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);

    repository = new CassandraWordCountRepository(session);
    repository.createKeyspaceAndTable().toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void afterAll() throws Exception {
    session
        .executeDDL("DROP keyspace pekko_projection.offset_store")
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
    session
        .executeDDL("DROP keyspace " + repository.keyspace)
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
  }

  private ProjectionTestKit projectionTestKit = ProjectionTestKit.create(testKit.system());

  private ProjectionId genRandomProjectionId() {
    return ProjectionId.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  private void runAndAssert(Projection<WordEnvelope> projection) {
    ProjectionId projectionId = projection.projectionId();
    Map<String, Integer> expected = new HashMap<>();
    expected.put("abc", 2);
    expected.put("def", 1);
    expected.put("ghi", 1);

    projectionTestKit.run(
        projection,
        () -> {
          Map<String, Integer> savedState =
              repository.loadAll(projectionId.id()).toCompletableFuture().get(3, TimeUnit.SECONDS);
          assertEquals(expected, savedState);
        });
  }

  @Test
  public void shouldLoadInitialStateAndManageUpdatedState() {
    ProjectionId projectionId = genRandomProjectionId();

    // #projection
    Projection<WordEnvelope> projection =
        CassandraProjection.atLeastOnce(
            projectionId, new WordSource(), () -> new WordCountHandler(projectionId, repository));
    // #projection

    runAndAssert(projection);
  }

  @Test
  public void shouldLoadStateOnDemandAndManageUpdatedState() {
    ProjectionId projectionId = genRandomProjectionId();

    Projection<WordEnvelope> projection =
        CassandraProjection.atLeastOnce(
            projectionId,
            new WordSource(),
            () ->
                new IllustrateStatefulHandlerLoadingStateOnDemand.WordCountHandler(
                    projectionId, repository));

    runAndAssert(projection);
  }

  @Test
  public void shouldSupportActorLoadInitialStateAndManageUpdatedState() {
    ProjectionId projectionId = genRandomProjectionId();
    ActorSystem<?> system = testKit.system();

    // #actorHandlerProjection
    Projection<WordEnvelope> projection =
        CassandraProjection.atLeastOnce(
            projectionId,
            new WordSource(),
            () ->
                new WordCountActorHandler(
                    WordCountProcessor.create(projectionId, repository), system));
    // #actorHandlerProjection

    runAndAssert(projection);
  }

  @Test
  public void shouldSupportActorLoadStateOnDemandAndManageUpdatedState() {
    ProjectionId projectionId = genRandomProjectionId();
    ActorSystem<?> system = testKit.system();

    Projection<WordEnvelope> projection =
        CassandraProjection.atLeastOnce(
            projectionId,
            new WordSource(),
            () ->
                new IllstrateActorLoadingStateOnDemand.WordCountActorHandler(
                    IllstrateActorLoadingStateOnDemand.WordCountProcessor.create(
                        projectionId, repository),
                    system));

    runAndAssert(projection);
  }
}
