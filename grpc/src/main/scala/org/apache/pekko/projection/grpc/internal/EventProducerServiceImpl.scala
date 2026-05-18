/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.internal

import pekko.Done
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.annotation.InternalApi
import pekko.grpc.GrpcServiceException
import pekko.grpc.scaladsl.Metadata
import pekko.persistence.query.NoOffset
import pekko.persistence.query.TimestampOffset
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.query.typed.scaladsl.CurrentEventsByPersistenceIdTypedQuery
import pekko.persistence.query.typed.scaladsl.EventTimestampQuery
import pekko.persistence.query.typed.scaladsl.EventsBySliceQuery
import pekko.persistence.query.typed.scaladsl.LoadEventQuery
import pekko.persistence.typed.PersistenceId
import pekko.projection.grpc.internal.proto.Event
import pekko.projection.grpc.internal.proto.EventProducerServicePowerApi
import pekko.projection.grpc.internal.proto.EventTimestampRequest
import pekko.projection.grpc.internal.proto.EventTimestampResponse
import pekko.projection.grpc.internal.proto.FilteredEvent
import pekko.projection.grpc.internal.proto.InitReq
import pekko.projection.grpc.internal.proto.LoadEventRequest
import pekko.projection.grpc.internal.proto.LoadEventResponse
import pekko.projection.grpc.internal.proto.Offset
import pekko.projection.grpc.internal.proto.PersistenceIdSeqNr
import pekko.projection.grpc.internal.proto.StreamIn
import pekko.projection.grpc.internal.proto.StreamOut
import pekko.projection.grpc.producer.scaladsl.EventProducer
import pekko.projection.grpc.producer.scaladsl.EventProducer.Transformation
import pekko.projection.grpc.producer.scaladsl.EventProducerInterceptor
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import com.google.protobuf.timestamp.Timestamp
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.Future
import scala.util.Success

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object EventProducerServiceImpl {
  val log: Logger =
    LoggerFactory.getLogger(classOf[EventProducerServiceImpl])
  private val futureDone = Future.successful(Done)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class EventProducerServiceImpl(
    system: ActorSystem[_],
    eventsBySlicesQueriesPerStreamId: Map[String, EventsBySliceQuery],
    currentEventsByPersistenceIdQueriesPerStreamId: Map[String, CurrentEventsByPersistenceIdTypedQuery],
    sources: Set[EventProducer.EventProducerSource],
    interceptor: Option[EventProducerInterceptor])
    extends EventProducerServicePowerApi {
  import EventProducerServiceImpl._
  import system.executionContext

  require(
    sources.nonEmpty,
    "Empty set of EventProducerSource passed to EventProducerService, must contain at least one")
  sources.foreach { s =>
    require(s.streamId.nonEmpty, s"EventProducerSource for [${s.entityType}] contains empty stream id, not allowed")
    require(
      eventsBySlicesQueriesPerStreamId.contains(s.streamId),
      s"No events by slices query defined for stream id [${s.streamId}]")
  }

  private val protoAnySerialization = new ProtoAnySerialization(system)

  private val streamIdToSourceMap: Map[String, EventProducer.EventProducerSource] =
    sources.map(s => s.streamId -> s).toMap

  log.info(
    s"Event producer gRPC service created with available sources [{}]",
    sources
      .map(s => s"(stream id: [${s.streamId}], entity type: [${s.entityType}])")
      .mkString(", "))

  private def intercept(streamId: String, metadata: Metadata): Future[Done] =
    interceptor match {
      case Some(interceptor) => interceptor.intercept(streamId, metadata)
      case None              => futureDone
    }

  private def eventProducerSourceFor(streamId: String): EventProducer.EventProducerSource =
    streamIdToSourceMap.getOrElse(
      streamId,
      throw new GrpcServiceException(
        Status.NOT_FOUND.withDescription(s"Stream id [${streamId}] is not available for consumption")))

  override def eventsBySlices(in: Source[StreamIn, NotUsed], metadata: Metadata): Source[StreamOut, NotUsed] = {
    in.prefixAndTail(1).flatMapConcat {
      case (Seq(StreamIn(StreamIn.Message.Init(init), _)), tail) =>
        tail.via(runEventsBySlices(init, metadata))
      case (Seq(), _) =>
        // if error during recovery in proxy the stream will be completed before init
        log.warn("Event stream closed before init.")
        Source.empty[StreamOut]
      case (Seq(StreamIn(other, _)), _) =>
        throw new IllegalArgumentException(
          "Expected init message for eventsBySlices stream, " +
          s"but received [${other.getClass.getName}]")
      case (seq, _) =>
        // silence warning: match may not be exhaustive
        throw new IllegalStateException(s"Unexpected Seq prefix with [${seq.size}] elements.")
    }
  }

  private def runEventsBySlices(init: InitReq, metadata: Metadata): Flow[StreamIn, StreamOut, NotUsed] = {
    val futureFlow = intercept(init.streamId, metadata).map { _ =>
      val producerSource = eventProducerSourceFor(init.streamId)

      val offset = init.offset match {
        case None => NoOffset
        case Some(o) =>
          val timestamp =
            o.timestamp.map(_.asJavaInstant).getOrElse(Instant.EPOCH)
          val seen = o.seen.map {
            case PersistenceIdSeqNr(pid, seqNr, _) =>
              pid -> seqNr
          }.toMap
          TimestampOffset(timestamp, seen)
      }

      log.debugN(
        "Starting eventsBySlices stream [{}], [{}], slices [{} - {}], offset [{}]",
        producerSource.streamId,
        producerSource.entityType,
        init.sliceMin,
        init.sliceMax,
        offset match {
          case t: TimestampOffset => t.timestamp
          case _                  => offset
        })

      val events: Source[EventEnvelope[Any], NotUsed] =
        eventsBySlicesQueriesPerStreamId(init.streamId)
          .eventsBySlices[Any](producerSource.entityType, init.sliceMin, init.sliceMax, offset)

      val eventsFlow: Flow[StreamIn, EventEnvelope[Any], NotUsed] =
        BidiFlow
          .fromGraph(
            new FilterStage(
              init.streamId,
              producerSource.entityType,
              init.sliceMin to init.sliceMax,
              init.filter,
              currentEventsByPersistenceIdQueriesPerStreamId(init.streamId),
              producerFilter = producerSource.producerFilter,
              replayParallelism = producerSource.settings.replayParallelism))
          .join(Flow.fromSinkAndSource(Sink.ignore, events))

      val eventsStreamOut: Flow[StreamIn, StreamOut, NotUsed] =
        eventsFlow.mapAsync(producerSource.settings.transformationParallelism) { env =>
          import system.executionContext
          transformAndEncodeEvent(producerSource.transformation, env).map {
            case Some(event) =>
              log.traceN(
                "Emitting event from persistenceId [{}] with seqNr [{}], offset [{}], source [{}]",
                env.persistenceId,
                env.sequenceNr,
                env.offset,
                event.source)
              StreamOut(StreamOut.Message.Event(event))
            case None =>
              log.traceN(
                "Filtered event from persistenceId [{}] with seqNr [{}], offset [{}], source [{}]",
                env.persistenceId,
                env.sequenceNr,
                env.offset,
                env.source)
              StreamOut(
                StreamOut.Message.FilteredEvent(
                  FilteredEvent(env.persistenceId, env.sequenceNr, env.slice, Some(protoOffset(env)))))
          }
        }

      eventsStreamOut
    }
    Flow.futureFlow(futureFlow).mapMaterializedValue(_ => NotUsed)
  }

  private def protoOffset(env: EventEnvelope[_]): Offset = {
    env.offset match {
      case TimestampOffset(timestamp, _, seen) =>
        val protoTimestamp = Timestamp(timestamp)
        val protoSeen = seen.iterator.map {
          case (pid, seqNr) =>
            PersistenceIdSeqNr(pid, seqNr)
        }.toSeq
        Offset(Some(protoTimestamp), protoSeen)
      case other =>
        throw new IllegalArgumentException(s"Unexpected offset type [$other]")
    }
  }

  private def transformAndEncodeEvent(transformation: Transformation, env: EventEnvelope[_]): Future[Option[Event]] = {
    env.eventOption match {
      case Some(_) =>
        import system.executionContext
        val mappedFuture: Future[Option[Any]] = transformation(env.asInstanceOf[EventEnvelope[Any]])
        def toEvent(transformedEvent: Any): Event = {
          val protoEvent = protoAnySerialization.serialize(transformedEvent)
          val metadata = env.eventMetadata.map(protoAnySerialization.serialize)
          Event(
            persistenceId = env.persistenceId,
            seqNr = env.sequenceNr,
            slice = env.slice,
            offset = Some(protoOffset(env)),
            payload = Some(protoEvent),
            metadata = metadata,
            source = env.source,
            tags = env.tags.toSeq)
        }
        mappedFuture.value match {
          case Some(Success(Some(transformedEvent))) => Future.successful(Some(toEvent(transformedEvent)))
          case Some(Success(None))                   => Future.successful(None)
          case _                                     => mappedFuture.map(_.map(toEvent))
        }

      case None =>
        // Events from backtracking are lazily loaded via `loadEvent` if needed.
        // Transformation and filter is done via `loadEvent` in that case.
        Future.successful(
          Some(
            Event(
              persistenceId = env.persistenceId,
              seqNr = env.sequenceNr,
              slice = env.slice,
              offset = Some(protoOffset(env)),
              payload = None,
              source = env.source,
              tags = env.tags.toSeq)))
    }
  }

  override def eventTimestamp(req: EventTimestampRequest, metadata: Metadata): Future[EventTimestampResponse] = {
    intercept(req.streamId, metadata).flatMap { _ =>
      val producerSource = streamIdToSourceMap(req.streamId)
      val entityTypeFromPid = PersistenceId.extractEntityType(req.persistenceId)
      if (entityTypeFromPid != producerSource.entityType) {
        throw new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(
          s"Persistence id is for a type of entity that is not available for consumption (expected type " +
          s" in persistence id for stream id [${req.streamId}] is [${producerSource.entityType}] but was [$entityTypeFromPid])"))
      }
      eventsBySlicesQueriesPerStreamId(req.streamId) match {
        case q: EventTimestampQuery =>
          import system.executionContext
          q.timestampOf(req.persistenceId, req.seqNr).map {
            case Some(instant) => EventTimestampResponse(Some(Timestamp(instant)))
            case None          => EventTimestampResponse.defaultInstance
          }
        case other =>
          Future.failed(
            new UnsupportedOperationException(s"eventTimestamp not supported by [${other.getClass.getName}]"))
      }
    }
  }

  override def loadEvent(req: LoadEventRequest, metadata: Metadata): Future[LoadEventResponse] = {
    intercept(req.streamId, metadata).flatMap { _ =>
      val producerSource = eventProducerSourceFor(req.streamId)
      val entityTypeFromPid = PersistenceId.extractEntityType(req.persistenceId)
      if (entityTypeFromPid != producerSource.entityType)
        throw new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(
          s"Persistence id is for a type of entity that is not available for consumption (expected type " +
          s" in persistence id for stream id [${req.streamId}] is [${producerSource.entityType}] but was [$entityTypeFromPid])"))
      eventsBySlicesQueriesPerStreamId(req.streamId) match {
        case q: LoadEventQuery =>
          import system.executionContext
          q.loadEnvelope[Any](req.persistenceId, req.seqNr)
            .flatMap { env =>
              transformAndEncodeEvent(producerSource.transformation, env).map {
                case Some(event) =>
                  log.traceN(
                    "Loaded event from persistenceId [{}] with seqNr [{}], offset [{}]",
                    env.persistenceId,
                    env.sequenceNr,
                    env.offset)
                  LoadEventResponse(LoadEventResponse.Message.Event(event))
                case None =>
                  log.traceN(
                    "Filtered loaded event from persistenceId [{}] with seqNr [{}], offset [{}]",
                    env.persistenceId,
                    env.sequenceNr,
                    env.offset)
                  LoadEventResponse(
                    LoadEventResponse.Message.FilteredEvent(
                      FilteredEvent(
                        persistenceId = env.persistenceId,
                        seqNr = env.sequenceNr,
                        slice = env.slice,
                        offset = Some(protoOffset(env)),
                        source = env.source)))
              }
            }
            .recoverWith {
              case e: NoSuchElementException =>
                log.warn(e.getMessage)
                Future.failed(new GrpcServiceException(Status.NOT_FOUND.withDescription(e.getMessage)))
            }
        case other =>
          Future.failed(new UnsupportedOperationException(s"loadEvent not supported by [${other.getClass.getName}]"))
      }
    }
  }
}
