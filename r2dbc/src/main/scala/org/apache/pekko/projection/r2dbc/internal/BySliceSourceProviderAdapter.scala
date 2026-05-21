/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc.internal

import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import scala.concurrent.Future
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.persistence.query.typed.EventEnvelope
import pekko.persistence.query.typed.scaladsl.EventTimestampQuery
import pekko.persistence.query.typed.scaladsl.LoadEventQuery
import pekko.projection.BySlicesSourceProvider
import pekko.projection.javadsl
import pekko.projection.scaladsl
import pekko.stream.scaladsl.Source

/**
 * INTERNAL API: Adapter from javadsl.SourceProvider to scaladsl.SourceProvider
 */
@InternalApi private[projection] class BySliceSourceProviderAdapter[Offset, Envelope](
    delegate: javadsl.SourceProvider[Offset, Envelope])
    extends scaladsl.SourceProvider[Offset, Envelope]
    with BySlicesSourceProvider
    with EventTimestampQuery
    with LoadEventQuery {

  def source(offset: () => Future[Option[Offset]]): Future[Source[Envelope, NotUsed]] = {
    // the parasitic context is used to convert the Optional to Option and a java streams Source to a scala Source,
    // it _should_ not be used for the blocking operation of getting offsets themselves
    val ec = scala.concurrent.ExecutionContext.parasitic
    val offsetAdapter = new Supplier[CompletionStage[Optional[Offset]]] {
      override def get(): CompletionStage[Optional[Offset]] = offset().map(_.toJava)(ec).asJava
    }
    delegate.source(offsetAdapter).asScala.map(_.asScala)(ec)
  }

  def extractOffset(envelope: Envelope): Offset = delegate.extractOffset(envelope)

  def extractCreationTime(envelope: Envelope): Long = delegate.extractCreationTime(envelope)

  override def minSlice: Int =
    delegate.asInstanceOf[BySlicesSourceProvider].minSlice

  override def maxSlice: Int =
    delegate.asInstanceOf[BySlicesSourceProvider].maxSlice

  override def timestampOf(persistenceId: String, sequenceNr: Long): Future[Option[Instant]] =
    delegate match {
      case timestampQuery: pekko.persistence.query.typed.javadsl.EventTimestampQuery =>
        timestampQuery.timestampOf(persistenceId, sequenceNr).asScala.map(_.toScala)(
          scala.concurrent.ExecutionContext.parasitic)
      case _ =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected SourceProvider [${delegate.getClass.getName}] to implement " +
            "EventTimestampQuery when TimestampOffset is used."))
    }

  override def loadEnvelope[Event](persistenceId: String, sequenceNr: Long): Future[EventEnvelope[Event]] =
    delegate match {
      case timestampQuery: pekko.persistence.query.typed.javadsl.LoadEventQuery =>
        timestampQuery.loadEnvelope[Event](persistenceId, sequenceNr).asScala
      case _ =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected SourceProvider [${delegate.getClass.getName}] to implement " +
            "EventTimestampQuery when LoadEventQuery is used."))
    }
}
