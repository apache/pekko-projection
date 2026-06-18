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

// #guideEvents
package jdocs.guide;

import java.time.Instant;

public class ShoppingCartEvents {
  public interface Event extends CborSerializable {
    String cartId();
  }

  public interface ItemEvent extends Event {
    String itemId();
  }

  public record ItemAdded(String cartId, String itemId, int quantity) implements ItemEvent {}

  public record ItemRemoved(String cartId, String itemId, int oldQuantity) implements ItemEvent {}

  public record ItemQuantityAdjusted(String cartId, String itemId, int newQuantity, int oldQuantity)
      implements ItemEvent {}

  public record CheckedOut(String cartId, Instant eventTime) implements Event {}
}
// #guideEvents
