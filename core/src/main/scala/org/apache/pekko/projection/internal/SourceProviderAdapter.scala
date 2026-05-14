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

package org.apache.pekko.projection.internal

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.projection.BySlicesSourceProvider
import pekko.projection.javadsl
import pekko.projection.scaladsl
import pekko.stream.scaladsl.Source
import pekko.stream.javadsl.{ Source => JSource }

/**
 * INTERNAL API: Adapter from javadsl.SourceProvider to scaladsl.SourceProvider
 */
@InternalApi private[projection] class SourceProviderAdapter[Offset, Envelope](
    delegate: javadsl.SourceProvider[Offset, Envelope])
    extends scaladsl.SourceProvider[Offset, Envelope] {

  def source(offset: () => Future[Option[Offset]]): Future[Source[Envelope, NotUsed]] = {
    // the parasitic context is used to convert the Optional to Option and a java streams Source to a scala Source,
    // it _should_ not be used for the blocking operation of getting offsets themselves
    val ec = ExecutionContext.parasitic
    val offsetAdapter = new Supplier[CompletionStage[Optional[Offset]]] {
      override def get(): CompletionStage[Optional[Offset]] = offset().map(_.toJava)(ec).asJava
    }
    delegate.source(offsetAdapter).asScala.map(_.asScala)(ec)
  }

  def extractOffset(envelope: Envelope): Offset = delegate.extractOffset(envelope)

  def extractCreationTime(envelope: Envelope): Long = delegate.extractCreationTime(envelope)
}

/**
 * INTERNAL API: Adapter from scaladsl.SourceProvider with BySlicesSourceProvider to javadsl.SourceProvider with BySlicesSourceProvider
 */
@InternalApi private[projection] class ScalaBySlicesSourceProviderAdapter[Offset, Envelope](
    delegate: scaladsl.SourceProvider[Offset, Envelope] with BySlicesSourceProvider)
    extends javadsl.SourceProvider[Offset, Envelope]
    with BySlicesSourceProvider {
  override def source(
      offset: Supplier[CompletionStage[Optional[Offset]]])
      : CompletionStage[JSource[Envelope, NotUsed]] =
    delegate
      .source(() => offset.get().asScala.map(_.toScala)(ExecutionContext.parasitic))
      .map(_.asJava)(ExecutionContext.parasitic)
      .asJava

  override def extractOffset(envelope: Envelope): Offset = delegate.extractOffset(envelope)

  override def extractCreationTime(envelope: Envelope): Long = delegate.extractCreationTime(envelope)

  def minSlice: Int = delegate.minSlice

  def maxSlice: Int = delegate.maxSlice
}
