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

package org.apache.pekko.projection.grpc.internal

import java.io.IOException

import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import org.apache.pekko.annotation.InternalApi

import io.grpc.Status

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object ConnectionException {

  /**
   * `UNAVAILABLE` is used both for failures of the connection itself and as a status returned by the
   * producer over a healthy connection. Only the former has a local I/O failure as the cause of the status,
   * since the cause is never transmitted from the server.
   */
  def isTransportFailure(status: Status): Boolean =
    status.getCode == Status.Code.UNAVAILABLE && hasIoCause(status.getCause)

  @tailrec private def hasIoCause(cause: Throwable): Boolean =
    cause match {
      case null           => false
      case _: IOException => true
      case other          => hasIoCause(other.getCause)
    }

  private def describe(status: Status): String = {
    val description = Option(status.getDescription).fold("")(d => s": $d")
    val cause = Option(status.getCause).fold("")(c => s", caused by $c")
    s"${status.getCode}$description$cause"
  }
}

/**
 * INTERNAL API
 *
 * The connection to the producer failed or was lost. This is expected to happen now and then, and the
 * stream is restarted, so there is no stack trace. The original gRPC `status`, including its cause, is retained.
 */
@InternalApi
private[pekko] final class ConnectionException(host: String, port: String, streamId: String, val status: Status)
    extends RuntimeException(
      s"Connection to $host:$port for stream id $streamId failed or lost (${ConnectionException.describe(status)})")
    with NoStackTrace
