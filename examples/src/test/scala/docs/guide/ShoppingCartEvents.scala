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

// #guideEvents
package docs.guide

import java.time.Instant

object ShoppingCartEvents {

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  sealed trait ItemEvent extends Event {
    def itemId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends ItemEvent
  final case class ItemRemoved(cartId: String, itemId: String, oldQuantity: Int) extends ItemEvent
  final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int, oldQuantity: Int)
      extends ItemEvent
  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event
}
// #guideEvents
