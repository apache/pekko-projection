/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.projections

import sbt.Keys._
import sbt._

object Dependencies {

  val Scala213 = "2.13.10"
  val Scala212 = "2.12.17"
  val ScalaVersions = Seq(Scala213, Scala212)

  val PekkoVersionInDocs = "0.0.0+26623-85c2a469-SNAPSHOT"
  val ConnectorsVersionInDocs = "0.0.0+60-c7dd80e0-SNAPSHOT"
  val ConnectorsKafkaVersionInDocs = "0.0.0+1717-267012de-SNAPSHOT"

  object Versions {
    val pekko = sys.props.getOrElse("build.pekko.version", "0.0.0+26623-85c2a469-SNAPSHOT")
    val pekkoPersistenceJdbc = "0.0.0+958-db5733e6-SNAPSHOT"
    val connectors = "0.0.0+60-c7dd80e0-SNAPSHOT"
    val connectorsKafka = sys.props.getOrElse("build.connectors.kafka.version", "0.0.0+1717-267012de-SNAPSHOT")
    val slick = "3.3.3"
    val scalaTest = "3.1.1"
    val testContainers = "1.15.3"
    val junit = "4.13.2"
    val h2Driver = "1.4.200"
    val jackson = "2.11.4" // this should match the version of jackson used by pekko-serialization-jackson
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

    val collectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
  }

  object Test {
    private val allTestConfig = "test,it"

    val pekkoTypedTestkit = Compile.pekkoTypedTestkit % allTestConfig
    val pekkoStreamTestkit = Compile.pekkoStreamTestkit % allTestConfig
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Versions.pekko % "test"

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % allTestConfig
    val scalatestJUnit = "org.scalatestplus" %% "junit-4-12" % (Versions.scalaTest + ".0") % allTestConfig
    val junit = "junit" % "junit" % Versions.junit % allTestConfig

    val h2Driver = Compile.h2Driver % allTestConfig
    val postgresDriver = "org.postgresql" % "postgresql" % "42.3.1" % allTestConfig
    val mysqlDriver = "mysql" % "mysql-connector-java" % "8.0.28" % allTestConfig
    val msSQLServerDriver = "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8" % allTestConfig
    val oracleDriver = "com.oracle.ojdbc" % "ojdbc8" % "19.3.0.0" % allTestConfig

    val logback = "ch.qos.logback" % "logback-classic" % "1.2.11" % allTestConfig

    val cassandraContainer =
      "org.testcontainers" % "cassandra" % Versions.testContainers % allTestConfig
    val postgresContainer =
      "org.testcontainers" % "postgresql" % Versions.testContainers % allTestConfig
    val mysqlContainer =
      "org.testcontainers" % "mysql" % Versions.testContainers % allTestConfig
    val msSQLServerContainer =
      "org.testcontainers" % "mssqlserver" % Versions.testContainers % allTestConfig

    val oracleDbContainer =
      "org.testcontainers" % "oracle-xe" % Versions.testContainers % allTestConfig

    val connectorsKafkaTestkit =
      "org.apache.pekko" %% "pekko-connectors-kafka-testkit" % Versions.connectorsKafka % allTestConfig
  }

  object Examples {
    val hibernate = "org.hibernate" % "hibernate-core" % "5.4.33"

    val pekkoPersistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % Versions.pekko
    val pekkoClusterShardingTyped = "org.apache.pekko" %% "pekko-cluster-sharding-typed" % Versions.pekko
    val pekkoPersistenceCassandra = "org.apache.pekko" %% "pekko-persistence-cassandra" % "0.0.0-1072-7c02b521-SNAPSHOT"
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
      Compile.collectionCompat,
      Test.scalatest,
      Test.scalatestJUnit,
      Test.junit,
      Test.logback)

  val eventsourced =
    deps ++= Seq(Compile.pekkoPersistenceQuery)

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
      Test.scalatestJUnit)

  val kafka =
    deps ++= Seq(
      Compile.connectorsKafka,
      Compile.jackson,
      Test.scalatest,
      Test.pekkoTypedTestkit,
      Test.pekkoStreamTestkit,
      Test.connectorsKafkaTestkit,
      Test.logback,
      Test.scalatestJUnit)

  val examples =
    deps ++= Seq(
      Examples.pekkoPersistenceTyped,
      Examples.pekkoClusterShardingTyped,
      Examples.pekkoPersistenceCassandra,
      Examples.pekkoPersistenceJdbc,
      Examples.hibernate,
      Test.h2Driver,
      Test.pekkoTypedTestkit,
      Test.logback,
      Test.cassandraContainer)
}
