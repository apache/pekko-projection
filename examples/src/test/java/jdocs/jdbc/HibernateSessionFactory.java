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

// #hibernate-factory-imports
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

// #hibernate-factory-imports

// #hibernate-factory
public class HibernateSessionFactory {
  private final EntityManagerFactory entityManagerFactory;

  public HibernateSessionFactory() {
    this.entityManagerFactory =
        Persistence.createEntityManagerFactory("pekko-projection-hibernate");
  }

  public HibernateJdbcSession newInstance() {
    return new HibernateJdbcSession(entityManagerFactory.createEntityManager());
  }
}
// #hibernate-factory
