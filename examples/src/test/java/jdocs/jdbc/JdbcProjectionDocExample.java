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

package jdocs.jdbc;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.japi.function.Function;

import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import jdocs.eventsourced.ShoppingCart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;

// #jdbc-session-imports
import org.apache.pekko.projection.jdbc.JdbcSession;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;

// #jdbc-session-imports

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@SuppressWarnings({"unused", "InnerClassMayBeStatic"})
class JdbcProjectionDocExample {
  // #todo
  // TODO
  // #todo

  // #repository
  class Order {
    public final String id;
    public final Instant time;

    public Order(String id, Instant time) {
      this.id = id;
      this.time = time;
    }
  }

  interface OrderRepository {
    void save(EntityManager entityManager, Order order);
  }
  // #repository

  @SuppressWarnings("Convert2Lambda")
  public OrderRepository orderRepository =
      new OrderRepository() {
        @Override
        public void save(EntityManager entityManager, Order order) {}
      };

  // #jdbc-session
  class PlainJdbcSession implements JdbcSession {

    private final Connection connection;

    public PlainJdbcSession() {
      try {
        Class.forName("org.h2.Driver");
        this.connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        connection.setAutoCommit(false);
      } catch (ClassNotFoundException | SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <Result> Result withConnection(Function<Connection, Result> func) throws Exception {
      return func.apply(connection);
    }

    @Override
    public void commit() throws SQLException {
      connection.commit();
    }

    @Override
    public void rollback() throws SQLException {
      connection.rollback();
    }

    @Override
    public void close() throws SQLException {
      connection.close();
    }
  }
  // #jdbc-session

  // #handler
  public class ShoppingCartHandler
      extends JdbcHandler<EventEnvelope<ShoppingCart.Event>, HibernateJdbcSession> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void process(HibernateJdbcSession session, EventEnvelope<ShoppingCart.Event> envelope)
        throws Exception {
      ShoppingCart.Event event = envelope.event();
      if (event instanceof ShoppingCart.CheckedOut) {
        ShoppingCart.CheckedOut checkedOut = (ShoppingCart.CheckedOut) event;
        logger.info(
            "Shopping cart {} was checked out at {}", checkedOut.cartId, checkedOut.eventTime);

        // pass the EntityManager created by the projection
        // to the repository in order to use the same transaction
        orderRepository.save(
            session.entityManager, new Order(checkedOut.cartId, checkedOut.eventTime));
      } else {
        logger.debug("Shopping cart {} changed by {}", event.getCartId(), event);
      }
    }
  }
  // #handler

  // #grouped-handler
  public class GroupedShoppingCartHandler
      extends JdbcHandler<List<EventEnvelope<ShoppingCart.Event>>, HibernateJdbcSession> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void process(
        HibernateJdbcSession session, List<EventEnvelope<ShoppingCart.Event>> envelopes)
        throws Exception {
      for (EventEnvelope<ShoppingCart.Event> envelope : envelopes) {
        ShoppingCart.Event event = envelope.event();
        if (event instanceof ShoppingCart.CheckedOut) {
          ShoppingCart.CheckedOut checkedOut = (ShoppingCart.CheckedOut) event;
          logger.info(
              "Shopping cart {} was checked out at {}", checkedOut.cartId, checkedOut.eventTime);

          // pass the EntityManager created by the projection
          // to the repository in order to use the same transaction
          orderRepository.save(
              session.entityManager, new Order(checkedOut.cartId, checkedOut.eventTime));

        } else {
          logger.debug("Shopping cart {} changed by {}", event.getCartId(), event);
        }
      }
    }
  }
  // #grouped-handler

  ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "Example");

  // #sourceProvider
  SourceProvider<Offset, EventEnvelope<ShoppingCart.Event>> sourceProvider =
      EventSourcedProvider.eventsByTag(system, CassandraReadJournal.Identifier(), "carts-1");
  // #sourceProvider

  {
    // #exactlyOnce
    final HibernateSessionFactory sessionProvider = new HibernateSessionFactory();

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        JdbcProjection.exactlyOnce(
            ProjectionId.of("shopping-carts", "carts-1"),
            sourceProvider,
            sessionProvider::newInstance,
            ShoppingCartHandler::new,
            system);
    // #exactlyOnce
  }

  {
    // #atLeastOnce
    final HibernateSessionFactory sessionProvider = new HibernateSessionFactory();
    int saveOffsetAfterEnvelopes = 100;
    Duration saveOffsetAfterDuration = Duration.ofMillis(500);

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        JdbcProjection.atLeastOnce(
                ProjectionId.of("shopping-carts", "carts-1"),
                sourceProvider,
                sessionProvider::newInstance,
                ShoppingCartHandler::new,
                system)
            .withSaveOffset(saveOffsetAfterEnvelopes, saveOffsetAfterDuration);
    // #atLeastOnce
  }

  {
    // #grouped
    final HibernateSessionFactory sessionProvider = new HibernateSessionFactory();
    int saveOffsetAfterEnvelopes = 100;
    Duration saveOffsetAfterDuration = Duration.ofMillis(500);

    Projection<EventEnvelope<ShoppingCart.Event>> projection =
        JdbcProjection.groupedWithin(
                ProjectionId.of("shopping-carts", "carts-1"),
                sourceProvider,
                sessionProvider::newInstance,
                GroupedShoppingCartHandler::new,
                system)
            .withGroup(saveOffsetAfterEnvelopes, saveOffsetAfterDuration);
    // #grouped
  }
}
