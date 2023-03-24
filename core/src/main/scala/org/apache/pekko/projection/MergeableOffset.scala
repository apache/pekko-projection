/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection

import org.apache.pekko.annotation.ApiMayChange

@ApiMayChange
final case class MergeableOffset[Offset](val entries: Map[String, Offset])
