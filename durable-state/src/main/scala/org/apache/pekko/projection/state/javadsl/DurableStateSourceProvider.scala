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

package org.apache.pekko.projection.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi
import pekko.japi.Pair
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.DurableStateChange
import pekko.persistence.query.UpdatedDurableState
import pekko.persistence.query.javadsl.DurableStateStoreQuery
import pekko.persistence.query.typed.javadsl.DurableStateStoreBySliceQuery
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.state.javadsl.DurableStateStore
import pekko.persistence.state.javadsl.GetObjectResult
import pekko.projection.BySlicesSourceProvider
import pekko.projection.javadsl
import pekko.projection.javadsl.SourceProvider
import pekko.stream.javadsl.Source
import pekko.util.FutureConverters._

/**
 * API may change
 */
@ApiMayChange
object DurableStateSourceProvider {
  def changesByTag[A](
      system: ActorSystem[_],
      pluginId: String,
      tag: String): SourceProvider[Offset, DurableStateChange[A]] = {
    val durableStateStoreQuery =
      DurableStateStoreRegistry(system)
        .getDurableStateStoreFor[DurableStateStoreQuery[A]](classOf[DurableStateStoreQuery[A]], pluginId)

    new DurableStateStoreQuerySourceProvider(durableStateStoreQuery, tag, system)
  }

  @InternalApi
  private class DurableStateStoreQuerySourceProvider[A](
      durableStateStoreQuery: DurableStateStoreQuery[A],
      tag: String,
      system: ActorSystem[_])
      extends javadsl.SourceProvider[Offset, DurableStateChange[A]] {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offsetAsync: Supplier[CompletionStage[Optional[Offset]]])
        : CompletionStage[Source[DurableStateChange[A], NotUsed]] = {
      val source: Future[Source[DurableStateChange[A], NotUsed]] = offsetAsync.get().asScala.map { offsetOpt =>
        durableStateStoreQuery
          .changes(tag, offsetOpt.orElse(NoOffset))
      }
      source.asJava
    }

    override def extractOffset(stateChange: DurableStateChange[A]): Offset = stateChange.offset

    override def extractCreationTime(stateChange: DurableStateChange[A]): Long =
      stateChange match {
        case u: UpdatedDurableState[_] => u.timestamp
        case _                         => 0L // FIXME handle DeletedDurableState when that is added
      }
  }

  def changesBySlices[A](
      system: ActorSystem[_],
      durableStateStoreQueryPluginId: String,
      entityType: String,
      minSlice: Int,
      maxSlice: Int): SourceProvider[Offset, DurableStateChange[A]] = {

    val durableStateStoreQuery =
      DurableStateStoreRegistry(system)
        .getDurableStateStoreFor(classOf[DurableStateStoreBySliceQuery[A]], durableStateStoreQueryPluginId)

    new DurableStateBySlicesSourceProvider(durableStateStoreQuery, entityType, minSlice, maxSlice, system)
  }

  def sliceForPersistenceId(
      system: ActorSystem[_],
      durableStateStoreQueryPluginId: String,
      persistenceId: String): Int =
    DurableStateStoreRegistry(system)
      .getDurableStateStoreFor(classOf[DurableStateStoreBySliceQuery[Any]], durableStateStoreQueryPluginId)
      .sliceForPersistenceId(persistenceId)

  def sliceRanges(
      system: ActorSystem[_],
      durableStateStoreQueryPluginId: String,
      numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] =
    DurableStateStoreRegistry(system)
      .getDurableStateStoreFor(classOf[DurableStateStoreBySliceQuery[Any]], durableStateStoreQueryPluginId)
      .sliceRanges(numberOfRanges)

  private class DurableStateBySlicesSourceProvider[A](
      durableStateStoreQuery: DurableStateStoreBySliceQuery[A],
      entityType: String,
      override val minSlice: Int,
      override val maxSlice: Int,
      system: ActorSystem[_])
      extends SourceProvider[Offset, DurableStateChange[A]]
      with BySlicesSourceProvider
      with DurableStateStore[A] {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offsetAsync: Supplier[CompletionStage[Optional[Offset]]])
        : CompletionStage[Source[DurableStateChange[A], NotUsed]] = {
      val source: Future[Source[DurableStateChange[A], NotUsed]] = offsetAsync.get().asScala.map { offsetOpt =>
        durableStateStoreQuery
          .changesBySlices(entityType, minSlice, maxSlice, offsetOpt.orElse(NoOffset))
      }
      source.asJava
    }

    override def extractOffset(stateChange: DurableStateChange[A]): Offset = stateChange.offset

    override def extractCreationTime(stateChange: DurableStateChange[A]): Long =
      stateChange match {
        case u: UpdatedDurableState[_] => u.timestamp
        case other                     =>
          // FIXME case DeletedDurableState when that is added
          throw new IllegalArgumentException(
            s"DurableStateChange [${other.getClass.getName}] not implemented yet. Please report bug at https://github.com/apache/incubator-pekko-persistence-r2dbc/issues")
      }

    override def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]] =
      durableStateStoreQuery.getObject(persistenceId)
  }
}
