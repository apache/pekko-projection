/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalApi

sealed trait OffsetVerification

@ApiMayChange
object OffsetVerification {
  case object VerificationSuccess extends OffsetVerification

  /** Java API */
  def verificationSuccess: OffsetVerification = VerificationSuccess

  final case class VerificationFailure[Offset](reason: String) extends OffsetVerification

  /** Java API */
  def verificationFailure(reason: String): OffsetVerification = VerificationFailure(reason)

  /**
   * Internal API
   *
   * Used when verifying offsets as part of transaction.
   */
  @InternalApi private[projection] case object VerificationFailureException
      extends RuntimeException("Offset verification failed")
      with NoStackTrace
}
