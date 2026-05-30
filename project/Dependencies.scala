/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.projections

import sbt.Keys._
import sbt._

object Dependencies {

  val Scala213 = "2.13.18"
  val Scala3 = "3.3.7"
  val ScalaVersions = Seq(Scala213, Scala3)

  val PekkoVersionInDocs = PekkoCoreDependency.default.link
  val PekkoGrpcVersionInDocs = "2.0"
  val PekkoPersistenceR2dbcVersionInDocs = PekkoPersistenceR2DBCDependency.default.link
  val ConnectorsVersionInDocs = PekkoConnectorsDependency.default.link
  val ConnectorsKafkaVersionInDocs = PekkoConnectorsKafkaDependency.default.link

  object Versions {
    val pekko = PekkoCoreDependency.version
    val pekkoGrpc = org.apache.pekko.grpc.gen.BuildInfo.version
    val pekkoPersistenceJdbc = PekkoPersistenceJDBCDependency.version
    val pekkoPersistenceR2dbc = PekkoPersistenceR2DBCDependency.version
    val pekkoPersistenceCassandra = "1.1.0"
    val connectors = PekkoConnectorsDependency.version
    val connectorsKafka = PekkoConnectorsKafkaDependency.version
    val slick = "3.6.1"
    val scalaTest = "3.2.20"
    val testContainers = "2.0.5"
    val junit = "4.13.2"
    val h2Driver = "2.4.240"
    val jackson = "2.21.3" // this should match the version of jackson used by pekko-serialization-jackson
    val logback = "1.5.32"
  }

  object Compile {
    val pekkoActorTyped = "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko
    val pekkoClusterShardingTyped = "org.apache.pekko" %% "pekko-cluster-sharding-typed" % Versions.pekko
    val pekkoStream = "org.apache.pekko" %% "pekko-stream" % Versions.pekko
    val pekkoProtobufV3 = "org.apache.pekko" %% "pekko-protobuf-v3" % Versions.pekko
    val pekkoPersistenceQuery = "org.apache.pekko" %% "pekko-persistence-query" % Versions.pekko
    val pekkoPersistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % Versions.pekko
    val pekkoGrpcRuntime = "org.apache.pekko" %% "pekko-grpc-runtime" % Versions.pekkoGrpc
    val pekkoPersistenceR2dbc = "org.apache.pekko" %% "pekko-persistence-r2dbc" % Versions.pekkoPersistenceR2dbc

    // TestKit in compile scope for ProjectionTestKit
    val pekkoTypedTestkit = "org.apache.pekko" %% "pekko-actor-testkit-typed" % Versions.pekko
    val pekkoStreamTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % Versions.pekko

    val slick = "com.typesafe.slick" %% "slick" % Versions.slick

    val connectorsCassandra = "org.apache.pekko" %% "pekko-connectors-cassandra" % Versions.connectors

    val connectorsKafka = "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.connectorsKafka

    // must be provided on classpath when using Apache Kafka 2.6.0+
    val jackson = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jackson

    val r2dbcSpi = "io.r2dbc" % "r2dbc-spi" % "1.0.0.RELEASE"
    val r2dbcPool = "io.r2dbc" % "r2dbc-pool" % "1.0.2.RELEASE"
    val r2dbcPostgres = "org.postgresql" % "r2dbc-postgresql" % "1.1.1.RELEASE"
    val r2dbcMysql = "io.asyncer" % "r2dbc-mysql" % "1.4.2"
  }

  object Test {
    val pekkoClusterShardingTyped = Compile.pekkoClusterShardingTyped % "test"
    val pekkoDiscovery = "org.apache.pekko" %% "pekko-discovery" % Versions.pekko % "test"
    val pekkoDistributedData = "org.apache.pekko" %% "pekko-distributed-data" % Versions.pekko % "test"
    val pekkoSerializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % Versions.pekko % "test"
    val pekkoTypedTestkit = Compile.pekkoTypedTestkit % "test"
    val pekkoStreamTestkit = Compile.pekkoStreamTestkit % "test"
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Versions.pekko % "test"

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"
    val scalatestJUnit = "org.scalatestplus" %% "junit-4-13" % (Versions.scalaTest + ".0")
    val junit = "junit" % "junit" % Versions.junit % "test"

    val h2Driver = "com.h2database" % "h2" % Versions.h2Driver % "test"
    val postgresDriver = "org.postgresql" % "postgresql" % "42.7.11" % "test"
    val mysqlDriver = "com.mysql" % "mysql-connector-j" % "9.7.0" % "test"
    val msSQLServerDriver = "com.microsoft.sqlserver" % "mssql-jdbc" % "13.4.0.jre11" % "test"
    val oracleDriver = "com.oracle.ojdbc" % "ojdbc8" % "19.3.0.0" % "test"

    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback % "test"

    val cassandraContainer =
      "org.testcontainers" % "testcontainers-cassandra" % Versions.testContainers % "test"
    val postgresContainer =
      "org.testcontainers" % "testcontainers-postgresql" % Versions.testContainers % "test"
    val mysqlContainer =
      "org.testcontainers" % "testcontainers-mysql" % Versions.testContainers % "test"
    val msSQLServerContainer =
      "org.testcontainers" % "testcontainers-mssqlserver" % Versions.testContainers % "test"

    val oracleDbContainer =
      "org.testcontainers" % "testcontainers-oracle-xe" % Versions.testContainers % "test"

    val connectorsKafkaTestkit =
      "org.apache.pekko" %% "pekko-connectors-kafka-testkit" % Versions.connectorsKafka

    val r2dbcPostgres = Compile.r2dbcPostgres % "test"
    val r2dbcMysql = Compile.r2dbcMysql % "test"
  }

  object Examples {
    val hibernate = "org.hibernate" % "hibernate-core" % "7.4.0.Final"

    val pekkoClusterShardingTyped = Compile.pekkoClusterShardingTyped
    val pekkoPersistenceCassandra =
      "org.apache.pekko" %% "pekko-persistence-cassandra" % Versions.pekkoPersistenceCassandra
    val pekkoPersistenceJdbc = "org.apache.pekko" %% "pekko-persistence-jdbc" % Versions.pekkoPersistenceJdbc
    val pekkoSerializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % Versions.pekko
  }

  private val deps = libraryDependencies

  val core =
    deps ++= Seq(
      Compile.pekkoStream,
      Compile.pekkoActorTyped,
      Compile.pekkoProtobufV3,
      // pekko-persistence-query is only needed for OffsetSerialization and to provide a typed EventEnvelope that
      // references the Offset type from pekko-persistence.
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit,
      Test.logback,
      Test.scalatest)

  val coreTest =
    deps ++= Seq(
      Test.pekkoTypedTestkit,
      Test.pekkoStreamTestkit,
      Test.scalatest,
      Test.scalatestJUnit,
      Test.junit,
      Test.logback)

  val testKit =
    deps ++= Seq(
      Compile.pekkoTypedTestkit,
      Compile.pekkoStreamTestkit,
      Test.scalatest,
      Test.scalatestJUnit,
      Test.junit,
      Test.logback)

  val eventsourced =
    deps ++= Seq(Compile.pekkoPersistenceQuery, Test.persistenceTestkit, Test.scalatest, Test.logback)

  val state =
    deps ++= Seq(Compile.pekkoPersistenceQuery, Test.persistenceTestkit, Test.pekkoStreamTestkit, Test.scalatest)

  val jdbc =
    deps ++= Seq(
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit,
      Test.h2Driver,
      Test.postgresDriver,
      Test.postgresContainer,
      Test.mysqlDriver,
      Test.mysqlContainer,
      Test.msSQLServerDriver,
      Test.msSQLServerContainer,
      Test.oracleDriver,
      Test.oracleDbContainer,
      Test.logback)

  val slick =
    deps ++= Seq(
      Compile.slick,
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit,
      Test.h2Driver,
      Test.postgresDriver,
      Test.postgresContainer,
      Test.mysqlDriver,
      Test.mysqlContainer,
      Test.msSQLServerDriver,
      Test.msSQLServerContainer,
      Test.oracleDriver,
      Test.oracleDbContainer,
      Test.logback)

  val cassandra =
    deps ++= Seq(
      Compile.connectorsCassandra,
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit,
      Test.logback,
      Test.cassandraContainer,
      Test.scalatest,
      Test.scalatestJUnit)

  val kafka =
    deps ++= Seq(
      Compile.connectorsKafka,
      Compile.jackson,
      Test.scalatest,
      Test.logback)

  val grpc =
    deps ++= Seq(
      Compile.pekkoGrpcRuntime,
      Compile.pekkoClusterShardingTyped % "provided",
      Compile.pekkoPersistenceQuery,
      Compile.pekkoPersistenceTyped,
      Compile.pekkoActorTyped,
      Compile.pekkoStream,
      Compile.pekkoProtobufV3,
      Compile.jackson)

  val grpcTest =
    deps ++= Seq(
      Test.postgresDriver,
      Test.pekkoClusterShardingTyped,
      Test.pekkoSerializationJackson,
      Test.pekkoDiscovery,
      Test.pekkoTypedTestkit,
      Test.pekkoStreamTestkit,
      Test.postgresContainer,
      Test.logback,
      Test.scalatest)

  val grpcIntTest =
    deps ++= Seq(
      Test.pekkoClusterShardingTyped,
      Test.postgresDriver,
      Test.pekkoSerializationJackson,
      Test.pekkoDiscovery,
      Test.pekkoTypedTestkit,
      Test.postgresContainer,
      Test.r2dbcPostgres,
      Test.logback,
      Test.scalatest)

  val r2dbc =
    deps ++= Seq(
      Compile.pekkoPersistenceR2dbc,
      Compile.pekkoPersistenceQuery,
      Compile.r2dbcSpi,
      Compile.r2dbcPool,
      Compile.r2dbcPostgres % "provided",
      Compile.r2dbcMysql % "provided",
      Test.pekkoClusterShardingTyped)

  val r2dbcIntTest =
    deps ++= Seq(
      Compile.pekkoPersistenceR2dbc,
      Compile.pekkoPersistenceQuery,
      Compile.r2dbcSpi,
      Compile.r2dbcPool,
      Test.r2dbcPostgres,
      Test.r2dbcMysql,
      Test.pekkoSerializationJackson,
      Test.pekkoDiscovery,
      Test.pekkoDistributedData,
      Test.pekkoTypedTestkit,
      Test.pekkoStreamTestkit,
      Test.logback,
      Test.scalatest)

  val kafkaTest =
    deps ++= Seq(
      Test.scalatest,
      Test.pekkoTypedTestkit,
      Test.pekkoStreamTestkit,
      Test.connectorsKafkaTestkit,
      Test.logback,
      Test.scalatestJUnit)

  val examples =
    deps ++= Seq(
      Compile.pekkoPersistenceTyped,
      Examples.pekkoClusterShardingTyped,
      Examples.pekkoPersistenceCassandra,
      Examples.pekkoPersistenceJdbc,
      Examples.hibernate,
      Test.h2Driver,
      Test.pekkoTypedTestkit,
      Test.logback,
      Test.cassandraContainer)
}
