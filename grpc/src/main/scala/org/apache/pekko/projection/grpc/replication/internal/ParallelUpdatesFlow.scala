/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.replication.internal

import pekko.Done
import pekko.annotation.InternalApi
import scala.concurrent.ExecutionContext
import pekko.persistence.query.typed.EventEnvelope
import pekko.projection.ProjectionContext
import pekko.stream.Attributes
import pekko.stream.FlowShape
import pekko.stream.Inlet
import pekko.stream.Outlet
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ParallelUpdatesFlow {
  final class Holder[T](val element: (EventEnvelope[T], ProjectionContext), var completed: Boolean) {
    def persistenceId: String = element._1.persistenceId
    def envelope: EventEnvelope[T] = element._1
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ParallelUpdatesFlow[T](parallelism: Int)(f: EventEnvelope[T] => Future[Done])
    extends GraphStage[FlowShape[(EventEnvelope[T], ProjectionContext), (EventEnvelope[T], ProjectionContext)]] {
  import ParallelUpdatesFlow._

  // Simpler version of MapAsyncPartitioned until that is ready, better than just mapAsync(1) but second element
  // for the same persistence id will block pulling elements for other pids until the original one has completed
  // FIXME replace with MapAsyncPartitioned once available

  val in = Inlet[(EventEnvelope[T], ProjectionContext)]("in")
  val out = Outlet[(EventEnvelope[T], ProjectionContext)]("out")
  val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var blockedByInFlight: Option[Holder[T]] = None
    private val inFlight = mutable.Queue[Holder[T]]()

    private val onCompleteCallback = getAsyncCallback(onComplete).invoke(_)

    private def onComplete(result: Try[String]): Unit = result match {
      case Success(persistenceId) =>
        inFlight.find(_.persistenceId == persistenceId).get.completed = true
        emitHeadIfPossible()

      case Failure(ex) => throw ex
    }

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val holder = new Holder[T](grab(in), false)
          if (inFlight.exists(_.persistenceId == holder.persistenceId)) {
            blockedByInFlight = Some(holder)
          } else {
            inFlight.enqueue(holder)
            processElement(holder)
            pullNextIfPossible()
          }
        }

        override def onUpstreamFinish(): Unit = {
          if (inFlight.isEmpty) completeStage()
          // else keep going and complete once queue is empty
        }
      })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (inFlight.nonEmpty && inFlight.head.completed) {
          emitHeadIfPossible()
        } else {
          pullNextIfPossible()
        }
      }
    })

    private def processElement(holder: Holder[T]): Unit = {
      f(holder.envelope)
        .map(_ => holder.persistenceId)(ExecutionContext.parasitic)
        .onComplete(onCompleteCallback)(ExecutionContext.parasitic)
    }

    private def emitHeadIfPossible(): Unit = {
      if (inFlight.head.completed && isAvailable(out)) {
        val head = inFlight.dequeue()
        push(out, head.element)
        blockedByInFlight match {
          case Some(blocked) =>
            if (blocked.persistenceId == head.persistenceId) {
              // we're now unblocked
              blockedByInFlight = None
              processElement(blocked)
              inFlight.enqueue(blocked)
              pullNextIfPossible()
            }
          case None =>
            pullNextIfPossible()
        }
        if (isClosed(in) && inFlight.isEmpty) completeStage()
      }
    }

    private def pullNextIfPossible(): Unit = {
      if (blockedByInFlight.isEmpty && inFlight.size < parallelism && !isClosed(in) && !hasBeenPulled(in)) {
        pull(in)
      }
    }
  }
}
