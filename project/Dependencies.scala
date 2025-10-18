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

  val Scala213 = "2.13.16"
  val Scala3 = "3.3.7"
  val ScalaVersions = Seq(Scala213, Scala3)

  val PekkoVersionInDocs = PekkoCoreDependency.default.link
  val ConnectorsVersionInDocs = PekkoConnectorsDependency.default.link
  val ConnectorsKafkaVersionInDocs = PekkoConnectorsKafkaDependency.default.link

  object Versions {
    val pekko = PekkoCoreDependency.version
    val pekkoPersistenceJdbc = "1.1.1"
    val pekkoPersistenceCassandra = "1.1.0"
    val connectors = PekkoConnectorsDependency.version
    val connectorsKafka = PekkoConnectorsKafkaDependency.version
    val slick = "3.6.1"
    val scalaTest = "3.2.19"
    val testContainers = "1.21.3"
    val junit = "4.13.2"
    val h2Driver = "2.4.240"
    val jackson = "2.20.0" // this should match the version of jackson used by pekko-serialization-jackson
    val logback = "1.5.19"
  }

  object Compile {
    val pekkoActorTyped = "org.apache.pekko" %% "pekko-actor-typed" % Versions.pekko
    val pekkoStream = "org.apache.pekko" %% "pekko-stream" % Versions.pekko
    val pekkoProtobufV3 = "org.apache.pekko" %% "pekko-protobuf-v3" % Versions.pekko
    val pekkoPersistenceQuery = "org.apache.pekko" %% "pekko-persistence-query" % Versions.pekko

    // TestKit in compile scope for ProjectionTestKit
    val pekkoTypedTestkit = "org.apache.pekko" %% "pekko-actor-testkit-typed" % Versions.pekko
    val pekkoStreamTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % Versions.pekko

    val slick = "com.typesafe.slick" %% "slick" % Versions.slick

    val connectorsCassandra = "org.apache.pekko" %% "pekko-connectors-cassandra" % Versions.connectors

    val connectorsKafka = "org.apache.pekko" %% "pekko-connectors-kafka" % Versions.connectorsKafka

    // must be provided on classpath when using Apache Kafka 2.6.0+
    val jackson = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jackson

    // not really used in lib code, but in example and test
    val h2Driver = "com.h2database" % "h2" % Versions.h2Driver
  }

  object TestNonIt {
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Versions.pekko % "test"

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"

    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback % "test"
  }

  object Test {
    val pekkoTypedTestkit = Compile.pekkoTypedTestkit
    val pekkoStreamTestkit = Compile.pekkoStreamTestkit
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Versions.pekko % "test"

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest
    val scalatestJUnit = "org.scalatestplus" %% "junit-4-13" % (Versions.scalaTest + ".0")
    val junit = "junit" % "junit" % Versions.junit

    val h2Driver = Compile.h2Driver
    val postgresDriver = "org.postgresql" % "postgresql" % "42.7.7"
    val mysqlDriver = "com.mysql" % "mysql-connector-j" % "9.4.0"
    val msSQLServerDriver = "com.microsoft.sqlserver" % "mssql-jdbc" % "13.2.0.jre11"
    val oracleDriver = "com.oracle.ojdbc" % "ojdbc8" % "19.3.0.0"

    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    val cassandraContainer =
      "org.testcontainers" % "cassandra" % Versions.testContainers
    val postgresContainer =
      "org.testcontainers" % "postgresql" % Versions.testContainers
    val mysqlContainer =
      "org.testcontainers" % "mysql" % Versions.testContainers
    val msSQLServerContainer =
      "org.testcontainers" % "mssqlserver" % Versions.testContainers

    val oracleDbContainer =
      "org.testcontainers" % "oracle-xe" % Versions.testContainers

    val connectorsKafkaTestkit =
      "org.apache.pekko" %% "pekko-connectors-kafka-testkit" % Versions.connectorsKafka
  }

  object Examples {
    val hibernate = "org.hibernate" % "hibernate-core" % "5.6.15.Final"

    val pekkoPersistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % Versions.pekko
    val pekkoClusterShardingTyped = "org.apache.pekko" %% "pekko-cluster-sharding-typed" % Versions.pekko
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
      Test.pekkoTypedTestkit % "test",
      Test.logback % "test",
      Test.scalatest % "test")

  val coreTest =
    deps ++= Seq(
      Test.pekkoTypedTestkit % "test",
      Test.pekkoStreamTestkit % "test",
      Test.scalatest % "test",
      Test.scalatestJUnit % "test",
      Test.junit % "test",
      Test.logback % "test")

  val testKit =
    deps ++= Seq(
      Compile.pekkoTypedTestkit,
      Compile.pekkoStreamTestkit,
      Test.scalatest % "test",
      Test.scalatestJUnit % "test",
      Test.junit % "test",
      Test.logback % "test")

  val eventsourced =
    deps ++= Seq(Compile.pekkoPersistenceQuery, TestNonIt.persistenceTestkit, TestNonIt.scalatest, TestNonIt.logback)

  val state =
    deps ++= Seq(Compile.pekkoPersistenceQuery, Test.persistenceTestkit, Test.pekkoStreamTestkit, Test.scalatest)

  val jdbc =
    deps ++= Seq(
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit % "test",
      Test.h2Driver % "test",
      Test.postgresDriver % "test",
      Test.postgresContainer % "test",
      Test.mysqlDriver % "test",
      Test.mysqlContainer % "test",
      Test.msSQLServerDriver % "test",
      Test.msSQLServerContainer % "test",
      Test.oracleDriver % "test",
      Test.oracleDbContainer % "test",
      Test.logback % "test")

  val slick =
    deps ++= Seq(
      Compile.slick,
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit % "test",
      Test.h2Driver % "test",
      Test.postgresDriver % "test",
      Test.postgresContainer % "test",
      Test.mysqlDriver % "test",
      Test.mysqlContainer % "test",
      Test.msSQLServerDriver % "test",
      Test.msSQLServerContainer % "test",
      Test.oracleDriver % "test",
      Test.oracleDbContainer % "test",
      Test.logback % "test")

  val cassandra =
    deps ++= Seq(
      Compile.connectorsCassandra,
      Compile.pekkoPersistenceQuery,
      Test.pekkoTypedTestkit % "test",
      Test.logback % "test",
      Test.cassandraContainer % "test",
      Test.scalatest % "test",
      Test.scalatestJUnit % "test")

  val kafka =
    deps ++= Seq(
      Compile.connectorsKafka,
      Compile.jackson,
      Test.scalatest % "test",
      Test.logback % "test")

  val kafkaTest =
    deps ++= Seq(
      Test.scalatest % "test",
      Test.pekkoTypedTestkit % "test",
      Test.pekkoStreamTestkit % "test",
      Test.connectorsKafkaTestkit % "test",
      Test.logback % "test",
      Test.scalatestJUnit % "test")

  val examples =
    deps ++= Seq(
      Examples.pekkoPersistenceTyped,
      Examples.pekkoClusterShardingTyped,
      Examples.pekkoPersistenceCassandra,
      Examples.pekkoPersistenceJdbc,
      Examples.hibernate,
      Test.h2Driver % "test",
      Test.pekkoTypedTestkit % "test",
      Test.logback % "test",
      Test.cassandraContainer % "test")
}
