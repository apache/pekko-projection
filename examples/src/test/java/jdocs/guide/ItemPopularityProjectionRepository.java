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

// #guideProjectionRepo
package jdocs.guide;

import org.apache.pekko.Done;
import org.apache.pekko.stream.connectors.cassandra.javadsl.CassandraSession;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

interface ItemPopularityProjectionRepository {
  CompletionStage<Done> update(String itemId, int delta);

  CompletionStage<Optional<Long>> getItem(String itemId);
}

class ItemPopularityProjectionRepositoryImpl implements ItemPopularityProjectionRepository {

  public static final String Keyspace = "pekko_projection";
  public static final String PopularityTable = "item_popularity";

  CassandraSession session;

  public ItemPopularityProjectionRepositoryImpl(CassandraSession session) {
    this.session = session;
  }

  @Override
  public CompletionStage<Done> update(String itemId, int delta) {
    return session.executeWrite(
        String.format(
            "UPDATE %s.%s SET count = count + ? WHERE item_id = ?", Keyspace, PopularityTable),
        (long) delta,
        itemId);
  }

  @Override
  public CompletionStage<Optional<Long>> getItem(String itemId) {
    return session
        .selectOne(
            String.format(
                "SELECT item_id, count FROM %s.%s WHERE item_id = ?", Keyspace, PopularityTable),
            itemId)
        .thenApply(opt -> opt.map(row -> row.getLong("count")));
  }
}
// #guideProjectionRepo
