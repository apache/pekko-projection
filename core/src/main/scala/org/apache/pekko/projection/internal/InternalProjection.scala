/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.internal

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[projection] trait InternalProjection {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[projection] def offsetStrategy: OffsetStrategy
}
