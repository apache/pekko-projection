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

package org.apache.pekko.projection.internal.metrics.tools

import scala.collection.immutable
import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.projection.ProjectionContext
import pekko.projection.internal.metrics.tools.InternalProjectionStateMetricsSpec.Envelope
import pekko.projection.scaladsl.Handler
import pekko.stream.scaladsl.FlowWithContext

/**
 */
object TestHandlers {

  trait ProcessStrategy

  case object AlwaysSucceed extends ProcessStrategy

  case class SomeFailures(erroredOffsets: List[Long]) extends ProcessStrategy {
    require(erroredOffsets.sorted == erroredOffsets)
  }

  object ProcessStrategy {
    def apply(erroredOffsets: List[Long]): ProcessStrategy =
      if (erroredOffsets.length == 0) AlwaysSucceed else new SomeFailures(erroredOffsets)
  }

  val single: () => Handler[Envelope] = singleWithErrors()

  /**
   * @param erroredOffsets a stack of errors. Must be ordered. Each item is an offset which, when observed will
   *                       trigger an error and then be removed from the stack. To fail an item multiple times
   *                       add its offset repeatedly. Uses `Int` instead of `Long` for convenience.
   */
  def singleWithErrors(erroredOffsets: Int*): () => Handler[Envelope] = {
    var nextProcessStrategy = ProcessStrategy(erroredOffsets.map {
      _.toLong
    }.toList)
    () =>
      new Handler[Envelope] {
        override def process(envelope: Envelope): Future[Done] = {
          nextProcessStrategy match {
            case SomeFailures(nextFail :: tail) if nextFail == envelope.offset =>
              nextProcessStrategy = SomeFailures(tail)
              throw TelemetryException
            case _ => Future.successful(Done)
          }
        }
      }
  }

  val grouped = groupedWithErrors()

  /**
   * @param erroredOffsets a stack of errors. Must be ordered. Each item is an offset which, when observed will
   *                       trigger an error and then be removed from the stack. To fail an item multiple times
   *                       add its offset repeatedly. Uses `Int` instead of `Long` for convenience.
   */
  def groupedWithErrors(erroredOffsets: Int*): () => Handler[immutable.Seq[Envelope]] = {
    var nextProcessStrategy = ProcessStrategy(erroredOffsets.map {
      _.toLong
    }.toList)
    () =>
      new Handler[immutable.Seq[Envelope]] {
        override def process(envelopes: immutable.Seq[Envelope]): Future[Done] = {
          nextProcessStrategy match {
            case SomeFailures(nextFail :: tail)
                if envelopes
                  .map {
                    _.offset
                  }
                  .contains(nextFail) =>
              nextProcessStrategy = SomeFailures(tail)
              Future.failed(TelemetryException)
            case _ =>
              Future.successful(Done)
          }
        }
      }
  }

  val flow = flowWithErrors()

  /**
   * @param erroredOffsets a stack of errors. Must be ordered. Each item is an offset which, when observed will
   *                       trigger an error and then be removed from the stack. To fail an item multiple times
   *                       add its offset repeatedly. Uses `Int` instead of `Long` for convenience.
   */
  def flowWithErrors(erroredOffsets: Int*) = {
    var nextProcessStrategy = ProcessStrategy(erroredOffsets.map {
      _.toLong
    }.toList)
    FlowWithContext[Envelope, ProjectionContext]
      .map { envelope =>
        nextProcessStrategy match {
          case SomeFailures(nextFail :: tail) if envelope.offset == nextFail =>
            nextProcessStrategy = SomeFailures(tail)
            throw TelemetryException
          case _ =>
            Done
        }
      }
  }

}
