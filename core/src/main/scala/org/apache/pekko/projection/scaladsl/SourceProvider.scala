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

package org.apache.pekko.projection.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.projection.MergeableOffset
import pekko.NotUsed
import pekko.annotation.ApiMayChange
import pekko.projection.OffsetVerification
import pekko.stream.scaladsl.Source

@ApiMayChange
trait SourceProvider[Offset, Envelope] {

  def source(offset: () => Future[Option[Offset]]): Future[Source[Envelope, NotUsed]]

  def extractOffset(envelope: Envelope): Offset

  /**
   * Timestamp (in millis-since-epoch) of the instant when the envelope was created. The meaning of "when the
   * envelope was created" is implementation specific and could be an instant on the producer machine, or the
   * instant when the database persisted the envelope, or other.
   */
  def extractCreationTime(envelope: Envelope): Long

}

@ApiMayChange
trait VerifiableSourceProvider[Offset, Envelope] extends SourceProvider[Offset, Envelope] {

  def verifyOffset(offset: Offset): OffsetVerification

}

@ApiMayChange
trait MergeableOffsetSourceProvider[Offset <: MergeableOffset[_], Envelope] extends SourceProvider[Offset, Envelope]
