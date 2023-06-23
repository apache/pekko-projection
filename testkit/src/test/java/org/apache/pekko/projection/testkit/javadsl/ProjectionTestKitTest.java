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

package org.apache.pekko.projection.testkit.javadsl;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.internal.ActorHandlerInit;
import org.apache.pekko.projection.internal.ProjectionSettings;
import org.apache.pekko.projection.RunningProjection;
import org.apache.pekko.projection.StatusObserver;
import org.apache.pekko.projection.internal.NoopStatusObserver;
import org.apache.pekko.stream.DelayOverflowStrategy;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.SharedKillSwitch;
import org.apache.pekko.stream.javadsl.DelayStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.FutureConverters;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import scala.Option;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class ProjectionTestKitTest extends JUnitSuite {

  private final List<Integer> elements =
      IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList());

  private final Source<Integer, NotUsed> src = Source.from(elements);

  private final Source<Integer, NotUsed> delayedSrc(long delayedBy) {
    return src.delayWith(
        () -> DelayStrategy.linearIncreasingDelay(Duration.ofMillis(delayedBy), __ -> true),
        DelayOverflowStrategy.backpressure());
  }

  @ClassRule public static final TestKitJunitResource testKitJunit = new TestKitJunitResource();

  private final ProjectionTestKit projectionTestKit =
      ProjectionTestKit.create(testKitJunit.system());

  @Test
  public void assertProgressOfAProjection() {
    StringBuffer strBuffer = new StringBuffer();
    TestProjection prj = new TestProjection(src, strBuffer, i -> i <= 6);

    projectionTestKit.run(prj, () -> assertEquals("1-2-3-4-5-6", strBuffer.toString()));
  }

  @Test
  public void retryAssertionFunctionUntilItSucceedsWithinAMaxTimeout() {

    StringBuffer strBuffer = new StringBuffer();
    TestProjection prj = new TestProjection(delayedSrc(100), strBuffer, i -> i <= 6);
    projectionTestKit.run(
        prj, Duration.ofSeconds(2), () -> assertEquals("1-2-3-4-5-6", strBuffer.toString()));
  }

  @Test
  public void retryAssertionFunctionAndFailWhenTimeoutExpires() {
    StringBuffer strBuffer = new StringBuffer();

    TestProjection prj = new TestProjection(delayedSrc(1000), strBuffer, i -> i <= 2);

    try {
      projectionTestKit.run(
          prj, Duration.ofSeconds(1), () -> assertEquals("1-2", strBuffer.toString()));
      Assert.fail("should not reach that line");
    } catch (ComparisonFailure failure) {
      // that was expected
    }
  }

  @Test
  public void failureInsideProjectionPropagatesToTestkit() {

    String streamFailureMsg = "stream failure";
    StringBuffer strBuffer = new StringBuffer();

    TestProjection prj =
        new TestProjection(
            src,
            strBuffer,
            i -> {
              if (i < 3) return true;
              else throw new RuntimeException(streamFailureMsg);
            });

    try {
      projectionTestKit.run(prj, () -> assertEquals("1-2-3-4", strBuffer.toString()));
      Assert.fail("should not reach that line");
    } catch (RuntimeException ex) {
      assertEquals(ex.getMessage(), streamFailureMsg);
    }
  }

  @Test
  public void failureInsideStreamPropagatesToTestkit() {

    String streamFailureMsg = "stream failure";
    StringBuffer strBuffer = new StringBuffer();

    Source<Integer, NotUsed> failingSource =
        Source.single(1).concat(Source.failed(new RuntimeException(streamFailureMsg)));

    TestProjection prj = new TestProjection(failingSource, strBuffer, i -> i <= 4);

    try {
      projectionTestKit.run(prj, () -> assertEquals("1-2-3-4", strBuffer.toString()));
      Assert.fail("should not reach that line");
    } catch (RuntimeException ex) {
      assertEquals(ex.getMessage(), streamFailureMsg);
    }
  }

  @Test
  public void runAProjectionWithATestSink() {

    StringBuffer strBuffer = new StringBuffer();
    List<Integer> elements = IntStream.rangeClosed(1, 5).boxed().collect(Collectors.toList());

    TestProjection prj = new TestProjection(Source.from(elements), strBuffer, i -> i <= 5);

    projectionTestKit.runWithTestSink(
        prj,
        sinkProbe -> {
          sinkProbe.request(5);
          sinkProbe.expectNextN(5);
          sinkProbe.expectComplete();
        });

    assertEquals(strBuffer.toString(), "1-2-3-4-5");
  }

  private class TestProjection implements Projection<Integer> {

    private final Source<Integer, NotUsed> src;
    private final StringBuffer strBuffer;
    private final Predicate<Integer> predicate;

    private TestProjection(
        Source<Integer, NotUsed> src, StringBuffer strBuffer, Predicate<Integer> predicate) {
      this.src = src;
      this.strBuffer = strBuffer;
      this.predicate = predicate;
    }

    @Override
    public ProjectionId projectionId() {
      return ProjectionId.of("test-projection", "00");
    }

    @Override
    public Projection<Integer> withRestartBackoff(
        FiniteDuration minBackoff, FiniteDuration maxBackoff, double randomFactor) {
      return this;
    }

    @Override
    public Projection<Integer> withRestartBackoff(
        FiniteDuration minBackoff,
        FiniteDuration maxBackoff,
        double randomFactor,
        int maxRestarts) {
      return this;
    }

    @Override
    public Projection<Integer> withRestartBackoff(
        Duration minBackoff, Duration maxBackoff, double randomFactor) {
      return this;
    }

    @Override
    public Projection<Integer> withRestartBackoff(
        Duration minBackoff, Duration maxBackoff, double randomFactor, int maxRestarts) {
      return this;
    }

    @Override
    public org.apache.pekko.stream.scaladsl.Source<Done, Future<Done>> mappedSource(
        ActorSystem<?> system) {
      return new InternalProjectionState(strBuffer, predicate, system).mappedSource();
    }

    @Override
    public <M> Option<ActorHandlerInit<M>> actorHandlerInit() {
      return Option.empty();
    }

    @Override
    public RunningProjection run(ActorSystem<?> system) {
      return new InternalProjectionState(strBuffer, predicate, system).newRunningInstance();
    }

    @Override
    public StatusObserver<Integer> statusObserver() {
      return NoopStatusObserver.getInstance();
    }

    @Override
    public Projection<Integer> withStatusObserver(StatusObserver<Integer> observer) {
      // no need for StatusObserver in tests
      return this;
    }

    /*
     * INTERNAL API
     * This internal class will hold the KillSwitch that is needed
     * when building the mappedSource and when running the projection (to stop)
     */
    private class InternalProjectionState {

      private final ActorSystem<?> system;
      private final SharedKillSwitch killSwitch;
      private final StringBuffer strBuffer;
      private final Predicate<Integer> predicate;

      private InternalProjectionState(
          StringBuffer strBuffer, Predicate<Integer> predicate, ActorSystem<?> system) {
        this.strBuffer = strBuffer;
        this.predicate = predicate;
        this.system = system;
        this.killSwitch = KillSwitches.shared(TestProjection.this.projectionId().id());
      }

      private CompletionStage<Done> process(Integer i) {
        if (predicate.test(i)) {
          if (strBuffer.toString().isEmpty()) strBuffer.append(i);
          else strBuffer.append("-").append(i);
        }
        return CompletableFuture.completedFuture(Done.getInstance());
      }

      private org.apache.pekko.stream.scaladsl.Source<Done, Future<Done>> mappedSource() {
        return src.via(killSwitch.flow())
            .mapAsync(1, (Function<Integer, CompletionStage<Done>>) this::process)
            .mapMaterializedValue(__ -> Future.successful(Done.getInstance()))
            .asScala();
      }

      private RunningProjection newRunningInstance() {
        return new TestRunningProjection(mappedSource(), killSwitch, system);
      }
    }

    private class TestRunningProjection implements RunningProjection {

      private final SharedKillSwitch killSwitch;
      private final Future<Done> futureDone;

      private TestRunningProjection(
          org.apache.pekko.stream.scaladsl.Source<Done, Future<Done>> source,
          SharedKillSwitch killSwitch,
          ActorSystem<?> system) {
        this.killSwitch = killSwitch;
        CompletionStage<Done> done = source.asJava().runWith(Sink.ignore(), system);
        this.futureDone = FutureConverters.asScala(done);
      }

      @Override
      public Future<Done> stop() {
        killSwitch.shutdown();
        return this.futureDone;
      }
    }
  }
}
