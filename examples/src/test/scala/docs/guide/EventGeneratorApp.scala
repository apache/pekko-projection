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

// #guideEventGeneratorApp
package docs.guide

import java.time.Instant

import scala.concurrent.duration._
import scala.util.Random

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import pekko.cluster.sharding.typed.scaladsl.Entity
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.cluster.typed.Cluster
import pekko.cluster.typed.Join
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory

/**
 * Generate a shopping cart every 1 second and check it out. Each cart will contain a variety of `ItemAdded`,
 * `ItemQuantityAdjusted` and `ItemRemoved` events preceding the the cart `Checkout` event.
 */
object EventGeneratorApp extends App {
  import ShoppingCartEvents._

  val Products = List("cat t-shirt", "pekko t-shirt", "skis", "bowling shoes")

  val MaxQuantity = 5
  val MaxItems = 3
  val MaxItemsAdjusted = 3

  val EntityKey: EntityTypeKey[Event] = EntityTypeKey[Event]("shopping-cart-event")

  val config = ConfigFactory
    .parseString("pekko.actor.provider = cluster")
    .withFallback(ConfigFactory.load("guide-shopping-cart-app.conf"))

  ActorSystem(Behaviors.setup[String] {
      ctx =>
        implicit val system = ctx.system
        val cluster = Cluster(system)
        cluster.manager ! Join(cluster.selfMember.address)
        val sharding = ClusterSharding(system)
        val _ = sharding.init(Entity(EntityKey) { entityCtx =>
          cartBehavior(entityCtx.entityId, tagFactory(entityCtx.entityId))
        })

        Source
          .tick(1.second, 1.second, "checkout")
          .mapConcat {
            case "checkout" =>
              val cartId = java.util.UUID.randomUUID().toString.take(5)
              val items = randomItems()
              val itemEvents = (0 to items).flatMap {
                _ =>
                  val itemId = Products(Random.nextInt(Products.size))

                  // add the item
                  val quantity = randomQuantity()
                  val itemAdded = ItemAdded(cartId, itemId, quantity)

                  // make up to `MaxItemAdjusted` adjustments to quantity of item
                  val adjustments = Random.nextInt(MaxItemsAdjusted)
                  val itemQuantityAdjusted = (0 to adjustments).foldLeft(Seq[ItemQuantityAdjusted]()) {
                    case (events, _) =>
                      val newQuantity = randomQuantity()
                      val oldQuantity =
                        if (events.isEmpty) itemAdded.quantity
                        else events.last.newQuantity
                      events :+ ItemQuantityAdjusted(cartId, itemId, newQuantity, oldQuantity)
                  }

                  // flip a coin to decide whether or not to remove the item
                  val itemRemoved =
                    if (Random.nextBoolean())
                      List(ItemRemoved(cartId, itemId, itemQuantityAdjusted.last.newQuantity))
                    else Nil

                  List(itemAdded) ++ itemQuantityAdjusted ++ itemRemoved
              }

              // checkout the cart and all its preceding item events
              itemEvents :+ CheckedOut(cartId, Instant.now())
          }
          // send each event to the sharded entity represented by the event's cartId
          .runWith(Sink.foreach(event => sharding.entityRefFor(EntityKey, event.cartId).ref.tell(event)))

        Behaviors.empty
    }, "EventGeneratorApp", config)

  /**
   * Random non-zero based quantity for `ItemAdded` and `ItemQuantityAdjusted` events
   */
  def randomQuantity(): Int = Random.nextInt(MaxQuantity - 1) + 1

  /**
   * Random non-zero based count for how many `ItemAdded` events to generate
   */
  def randomItems(): Int = Random.nextInt(MaxItems - 1) + 1

  /**
   * Choose a tag from `ShoppingCartTags` based on the entity id (cart id)
   */
  def tagFactory(entityId: String): String =
    if (args.contains("cluster")) {
      val n = math.abs(entityId.hashCode % ShoppingCartTags.Tags.size)
      val selectedTag = ShoppingCartTags.Tags(n)
      selectedTag
    } else ShoppingCartTags.Single

  /**
   * Construct an Actor that persists shopping cart events for a particular persistence id (cart id) and tag.
   * This is not how real Event Sourced actors should be be implemented. Please look at
   * https://pekko.apache.org/docs/pekko/current/typed/persistence.html for more information about `EventSourcedBehavior`.
   */
  def cartBehavior(persistenceId: String, tag: String): Behavior[Event] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Event, Event, List[Any]](
        persistenceId = PersistenceId.ofUniqueId(persistenceId),
        Nil,
        (_, event) => {
          ctx.log.info("id [{}] tag [{}] event: {}", persistenceId, tag, event)
          Effect.persist(event)
        },
        (_, _) => Nil).withTagger(_ => Set(tag))
    }
}
// #guideEventGeneratorApp
