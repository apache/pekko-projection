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

  val Scala213 = "2.13.15"
  val Scala212 = "2.12.20"
  val Scala3 = "3.3.4"
  val ScalaVersions = Seq(Scala213, Scala212, Scala3)

  val PekkoVersionInDocs = PekkoCoreDependency.default.link
  val ConnectorsVersionInDocs = PekkoConnectorsDependency.default.link
  val ConnectorsKafkaVersionInDocs = PekkoConnectorsKafkaDependency.default.link

  object Versions {
    val slick = "3.5.1"
    val scalaTest = "3.2.19"
    val testContainers = "1.20.4"
    val junit = "4.13.2"
    val h2Driver = "2.2.224"
    val jackson = "2.17.3" // this should match the version of jackson used by pekko-serialization-jackson
    val logback = "1.3.14"
  }

  object Compile {
    val slick = "com.typesafe.slick" %% "slick" % Versions.slick

    // must be provided on classpath when using Apache Kafka 2.6.0+
    val jackson = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jackson

    // not really used in lib code, but in example and test
    val h2Driver = "com.h2database" % "h2" % Versions.h2Driver
  }

  object Test {
    private val allTestConfig = "test,it"

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % allTestConfig
    val scalatestJUnit = "org.scalatestplus" %% "junit-4-13" % (Versions.scalaTest + ".0") % allTestConfig
    val junit = "junit" % "junit" % Versions.junit % allTestConfig

    val h2Driver = Compile.h2Driver % allTestConfig
    val postgresDriver = "org.postgresql" % "postgresql" % "42.7.4" % allTestConfig
    val mysqlDriver = "com.mysql" % "mysql-connector-j" % "9.1.0" % allTestConfig
    val msSQLServerDriver = "com.microsoft.sqlserver" % "mssql-jdbc" % "12.8.1.jre8" % allTestConfig
    val oracleDriver = "com.oracle.ojdbc" % "ojdbc8" % "19.3.0.0" % allTestConfig

    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback % allTestConfig

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
  }

  object Examples {
    val hibernate = "org.hibernate" % "hibernate-core" % "5.6.15.Final"
  }

  private val deps = libraryDependencies

  val core =
    deps ++= Seq(
      Test.logback,
      Test.scalatest)

  val coreTest =
    deps ++= Seq(
      Test.scalatest,
      Test.scalatestJUnit,
      Test.junit,
      Test.logback)

  val testKit =
    deps ++= Seq(
      Test.scalatest,
      Test.scalatestJUnit,
      Test.junit,
      Test.logback)

  val state =
    deps ++= Seq(Test.scalatest)

  val jdbc =
    deps ++= Seq(
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
      Test.logback,
      Test.cassandraContainer,
      Test.scalatestJUnit)

  val kafka =
    deps ++= Seq(
      Compile.jackson)

  val kafkaTest =
    deps ++= Seq(
      Test.scalatest,
      Test.logback,
      Test.scalatestJUnit)

  val examples =
    deps ++= Seq(
      Examples.hibernate,
      Test.h2Driver,
      Test.logback,
      Test.cassandraContainer)
}
