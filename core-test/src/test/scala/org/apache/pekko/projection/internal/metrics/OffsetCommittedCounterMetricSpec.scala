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

package org.apache.pekko.projection.internal.metrics

import scala.concurrent.duration._

import org.apache.pekko
import pekko.projection.HandlerRecoveryStrategy
import pekko.projection.ProjectionId
import pekko.projection.internal.AtLeastOnce
import pekko.projection.internal.AtMostOnce
import pekko.projection.internal.ExactlyOnce
import pekko.projection.internal.FlowHandlerStrategy
import pekko.projection.internal.GroupedHandlerStrategy
import pekko.projection.internal.SingleHandlerStrategy
import pekko.projection.internal.metrics.tools.InMemInstrumentsRegistry
import pekko.projection.internal.metrics.tools.InternalProjectionStateMetricsSpec
import pekko.projection.internal.metrics.tools.InternalProjectionStateMetricsSpec.Envelope
import pekko.projection.internal.metrics.tools.InternalProjectionStateMetricsSpec.TelemetryTester
import pekko.projection.internal.metrics.tools.TestHandlers

sealed abstract class OffsetCommittedCounterMetricSpec extends InternalProjectionStateMetricsSpec {

  implicit var projectionId: ProjectionId = null

  before {
    projectionId = genRandomProjectionId()
  }

  def instruments(implicit projectionId: ProjectionId) = InMemInstrumentsRegistry(system).forId(projectionId)
  val defaultNumberOfEnvelopes = 6

}

class OffsetCommittedCounterMetricAtLeastOnceSpec extends OffsetCommittedCounterMetricSpec {

  "A metric counting offsets committed" must {
    // at-least-once
    " in `at-least-once` with singleHandler" must {
      "count offsets (without afterEnvelops optimization)" in {
        val numberOfEnvelopes = 6
        val single = TestHandlers.single
        val tt = new TelemetryTester(
          AtLeastOnce(afterEnvelopes = Some(1)),
          SingleHandlerStrategy(single),
          numberOfEnvelopes = numberOfEnvelopes)

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(numberOfEnvelopes)
          instruments.onOffsetStoredInvocations.get should be(numberOfEnvelopes)
        }
      }
      "count offsets (with afterEnvelops optimization)" in {
        val batchSize = 3
        val numberOfEnvelopes = 6
        val tt =
          new TelemetryTester(
            AtLeastOnce(afterEnvelopes = Some(batchSize)),
            SingleHandlerStrategy(TestHandlers.single),
            numberOfEnvelopes = numberOfEnvelopes)

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(numberOfEnvelopes)
          instruments.onOffsetStoredInvocations.get should be(2)
        }
      }
      "count offsets only once in case of failure" in {
        val single = TestHandlers.singleWithErrors(1, 2, 3, 4, 4, 4, 4, 5, 6)
        val tt = new TelemetryTester(
          AtLeastOnce(
            afterEnvelopes = Some(1),
            // using retryAndFail to try to get all message through
            recoveryStrategy = Some(HandlerRecoveryStrategy.retryAndFail(maxRetries, 30.millis))),
          SingleHandlerStrategy(single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(6)
        }
      }
    }
    " in `at-least-once` with groupedHandler" must {
      "count offsets (without afterEnvelops optimization)" in {
        val tt = new TelemetryTester(
          AtLeastOnce(afterEnvelopes = Some(1)),
          GroupedHandlerStrategy(TestHandlers.grouped, afterEnvelopes = Some(2), orAfterDuration = Some(50.millis)))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(3)
        }
      }
      "count offsets (with afterEnvelops optimization)" in {
        val tt = new TelemetryTester(
          AtLeastOnce(afterEnvelopes = Some(3)),
          GroupedHandlerStrategy(TestHandlers.grouped, afterEnvelopes = Some(2), orAfterDuration = Some(50.millis)))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(1)
        }
      }
      "count envelopes only once in case of failure" in {
        val grouped = TestHandlers.groupedWithErrors(2, 3, 4)
        val tt = new TelemetryTester(
          AtLeastOnce(
            afterEnvelopes = Some(2),
            recoveryStrategy = Some(HandlerRecoveryStrategy.retryAndFail(maxRetries, 30.millis))),
          GroupedHandlerStrategy(grouped, afterEnvelopes = Some(3), orAfterDuration = Some(50.millis)))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(1)
        }
      }
    }
    " in `at-least-once` with flowHandler" must {
      "count offsets" in {
        val tt =
          new TelemetryTester(AtLeastOnce(afterEnvelopes = Some(3)), FlowHandlerStrategy[Envelope](TestHandlers.flow))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(2)
        }
      }
      "count offsets only once in case of failure" in {
        val flow = TestHandlers.flowWithErrors(1, 2, 3)
        val tt =
          new TelemetryTester(AtLeastOnce(afterEnvelopes = Some(2)), FlowHandlerStrategy[Envelope](flow))
        runInternal(tt.projectionState) {
          instruments.onOffsetStoredInvocations.get should be(3)
          instruments.offsetsSuccessfullyCommitted.get should be(6)
        }
      }
    }

  }

}

class OffsetCommittedCounterMetricExactlyOnceSpec extends OffsetCommittedCounterMetricSpec {

  "A metric counting offsets committed" must {

    // exactly-once
    " in `exactly-once` with singleHandler" must {
      "count offsets" in {
        val tt =
          new TelemetryTester(ExactlyOnce(), SingleHandlerStrategy(TestHandlers.single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(6)
        }
      }
      "count offsets only once in case of failure" in {
        val single = TestHandlers.singleWithErrors(1, 1, 3, 4, 5, 5, 5, 5, 6)
        val tt = new TelemetryTester(
          // using retryAndFail to try to get all message through
          ExactlyOnce(recoveryStrategy = Some(HandlerRecoveryStrategy.retryAndFail(maxRetries, 30.millis))),
          SingleHandlerStrategy(single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
        }
      }
    }
    " in `exactly-once` with groupedHandler" must {
      "count offsets" in {
        val grouped = TestHandlers.grouped
        val groupHandler = GroupedHandlerStrategy(grouped, afterEnvelopes = Some(2))
        val tt =
          new TelemetryTester(ExactlyOnce(), groupHandler)

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(3)
        }
      }
      "count offsets only once in case of failure" in {
        val groupedWithFailures = TestHandlers.groupedWithErrors(1, 2, 3)
        val tt = new TelemetryTester(
          ExactlyOnce(recoveryStrategy = Some(HandlerRecoveryStrategy.retryAndFail(maxRetries, 30.millis))),
          GroupedHandlerStrategy(groupedWithFailures, afterEnvelopes = Some(2)))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
        }
      }
    }

  }

}

class OffsetCommittedCounterMetricAtMostOnceSpec extends OffsetCommittedCounterMetricSpec {

  "A metric counting offsets committed" must {

    // at-most-once
    " in `at-most-once` with singleHandler" must {
      "count offsets" in {
        val tt =
          new TelemetryTester(ExactlyOnce(), SingleHandlerStrategy(TestHandlers.single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
          instruments.onOffsetStoredInvocations.get should be(6)
        }
      }
      "count offsets once in case of failure (skip)" in {
        val single = TestHandlers.singleWithErrors(2, 3, 4)
        val tt = new TelemetryTester(
          AtMostOnce(recoveryStrategy = Some(HandlerRecoveryStrategy.skip)),
          SingleHandlerStrategy(single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
        }
      }
      "count offsets once in case of failure (fail)" in {
        val single = TestHandlers.singleWithErrors(4, 5, 6)
        val tt = new TelemetryTester(
          AtMostOnce(recoveryStrategy = Some(HandlerRecoveryStrategy.fail)),
          SingleHandlerStrategy(single))

        runInternal(tt.projectionState) {
          instruments.offsetsSuccessfullyCommitted.get should be(6)
        }
      }
    }

  }

}
