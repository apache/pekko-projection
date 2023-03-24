/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.state.scaladsl

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.typed.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.persistence.query.NoOffset
import pekko.persistence.query.Offset
import pekko.persistence.query.DurableStateChange
import pekko.persistence.query.UpdatedDurableState
import pekko.persistence.query.scaladsl.DurableStateStoreQuery
import pekko.persistence.query.typed.scaladsl.DurableStateStoreBySliceQuery
import pekko.persistence.state.DurableStateStoreRegistry
import pekko.persistence.state.scaladsl.DurableStateStore
import pekko.persistence.state.scaladsl.GetObjectResult
import pekko.projection.BySlicesSourceProvider
import pekko.projection.scaladsl.SourceProvider
import pekko.stream.scaladsl.Source

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
      DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateStoreQuery[A]](pluginId)

    new DurableStateStoreQuerySourceProvider(durableStateStoreQuery, tag, system)
  }

  private class DurableStateStoreQuerySourceProvider[A](
      durableStateStoreQuery: DurableStateStoreQuery[A],
      tag: String,
      system: ActorSystem[_])
      extends SourceProvider[Offset, DurableStateChange[A]] {
    implicit val executionContext: ExecutionContext = system.executionContext

    override def source(offset: () => Future[Option[Offset]]): Future[Source[DurableStateChange[A], NotUsed]] =
      offset().map { offsetOpt =>
        val offset = offsetOpt.getOrElse(NoOffset)
        durableStateStoreQuery
          .changes(tag, offset)
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
        .durableStateStoreFor[DurableStateStoreBySliceQuery[A]](durableStateStoreQueryPluginId)

    new DurableStateBySlicesSourceProvider(durableStateStoreQuery, entityType, minSlice, maxSlice, system)
  }

  def sliceForPersistenceId(
      system: ActorSystem[_],
      durableStateStoreQueryPluginId: String,
      persistenceId: String): Int =
    DurableStateStoreRegistry(system)
      .durableStateStoreFor[DurableStateStoreBySliceQuery[Any]](durableStateStoreQueryPluginId)
      .sliceForPersistenceId(persistenceId)

  def sliceRanges(
      system: ActorSystem[_],
      durableStateStoreQueryPluginId: String,
      numberOfRanges: Int): immutable.Seq[Range] =
    DurableStateStoreRegistry(system)
      .durableStateStoreFor[DurableStateStoreBySliceQuery[Any]](durableStateStoreQueryPluginId)
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

    override def source(offset: () => Future[Option[Offset]]): Future[Source[DurableStateChange[A], NotUsed]] =
      offset().map { offsetOpt =>
        val offset = offsetOpt.getOrElse(NoOffset)
        durableStateStoreQuery
          .changesBySlices(entityType, minSlice, maxSlice, offset)
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

    override def getObject(persistenceId: String): Future[GetObjectResult[A]] =
      durableStateStoreQuery.getObject(persistenceId)
  }
}
