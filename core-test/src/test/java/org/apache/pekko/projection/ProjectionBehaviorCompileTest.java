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

package org.apache.pekko.projection;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.projection.internal.ActorHandlerInit;
import org.apache.pekko.projection.internal.NoopStatusObserver;
import org.apache.pekko.projection.internal.ProjectionSettings;
import org.apache.pekko.stream.scaladsl.Source;
import scala.Option;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;

/** Compile test: this class serves only for exercising the Java API. */
public class ProjectionBehaviorCompileTest {

  public void compileTest() {
    ActorTestKit testKit = ActorTestKit.create();
    ActorRef<ProjectionBehavior.Command> ref =
        testKit.spawn(ProjectionBehavior.create(new TestProjection()));
    ref.tell(ProjectionBehavior.stopMessage());
    // nobody is calling this method, so not really starting the system,
    // but we never know
    testKit.shutdownTestKit();
  }

  static class TestProjection implements Projection<String> {

    @Override
    public ProjectionId projectionId() {
      return null;
    }

    @Override
    public Source<Done, Future<Done>> mappedSource(ActorSystem<?> system) {
      return null;
    }

    @Override
    public <M> Option<ActorHandlerInit<M>> actorHandlerInit() {
      return Option.empty();
    }

    @Override
    public RunningProjection run(ActorSystem<?> system) {
      return null;
    }

    @Override
    public StatusObserver<String> statusObserver() {
      return NoopStatusObserver.getInstance();
    }

    @Override
    public Projection<String> withStatusObserver(StatusObserver<String> observer) {
      // no need for StatusObserver in tests
      return this;
    }

    @Override
    public Projection<String> withRestartBackoff(
        FiniteDuration minBackoff, FiniteDuration maxBackoff, double randomFactor) {
      return this;
    }

    @Override
    public Projection<String> withRestartBackoff(
        FiniteDuration minBackoff,
        FiniteDuration maxBackoff,
        double randomFactor,
        int maxRestarts) {
      return this;
    }

    @Override
    public Projection<String> withRestartBackoff(
        Duration minBackoff, Duration maxBackoff, double randomFactor) {
      return this;
    }

    @Override
    public Projection<String> withRestartBackoff(
        Duration minBackoff, Duration maxBackoff, double randomFactor, int maxRestarts) {
      return this;
    }
  }
}
