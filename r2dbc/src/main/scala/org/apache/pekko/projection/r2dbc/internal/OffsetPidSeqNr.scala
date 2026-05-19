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

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object OffsetPidSeqNr {
  def apply(offset: Any, pid: String, seqNr: Long): OffsetPidSeqNr =
    new OffsetPidSeqNr(offset, Some(pid -> seqNr))

  def apply(offset: Any): OffsetPidSeqNr =
    new OffsetPidSeqNr(offset, None)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class OffsetPidSeqNr(offset: Any, pidSeqNr: Option[(String, Long)])
