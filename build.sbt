/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsCheckResolver
import org.apache.pekko.projections.Dependencies

ThisBuild / apacheSonatypeProjectProfile := "pekko"
ThisBuild / versionScheme := Some(VersionScheme.SemVerSpec)
sourceDistName := "apache-pekko-projection"
sourceDistIncubating := true

commands := commands.value.filterNot { command =>
  command.nameOption.exists { name =>
    name.contains("sonatypeRelease") || name.contains("sonatypeBundleRelease")
  }
}

ThisBuild / reproducibleBuildsCheckResolver :=
  "Apache Pekko Staging".at("https://repository.apache.org/content/groups/staging/")

// TODO: Remove when Pekko-Connectors has a proper release
ThisBuild / resolvers += Resolver.ApacheMavenSnapshotsRepo
ThisBuild / updateOptions := updateOptions.value.withLatestSnapshots(false)

lazy val core =
  Project(id = "core", base = file("core"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.core)
    .settings(
      name := "pekko-projection-core",
      Compile / packageBin / packageOptions += Package.ManifestAttributes(
        "Automatic-Module-Name" -> "pekko.projection.core"))
    .settings(Protobuf.settings)

lazy val coreTest =
  Project(id = "core-test", base = file("core-test"))
    .configs(IntegrationTest)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(headerSettings(IntegrationTest))
    .disablePlugins(MimaPlugin)
    .settings(Defaults.itSettings)
    .settings(Dependencies.coreTest)
    .settings(
      name := "pekko-projection-core-test")
    .settings(publish / skip := true)
    .dependsOn(core)
    .dependsOn(testkit % Test)

lazy val testkit =
  Project(id = "testkit", base = file("testkit"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.testKit)
    .settings(
      name := "pekko-projection-testkit")
    .dependsOn(core)

// provides offset storage backed by a JDBC table
lazy val jdbc =
  Project(id = "jdbc", base = file("jdbc"))
    .configs(IntegrationTest.extend(Test))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.jdbc)
    .settings(
      name := "pekko-projection-jdbc")
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides offset storage backed by a JDBC (Slick) table
lazy val slick =
  Project(id = "slick", base = file("slick"))
    .configs(IntegrationTest.extend(Test))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2Versions)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.slick)
    .settings(
      name := "pekko-projection-slick")
    .dependsOn(jdbc)
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides offset storage backed by a Cassandra table
lazy val cassandra =
  Project(id = "cassandra", base = file("cassandra"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.cassandra)
    .settings(
      name := "pekko-projection-cassandra")
    .dependsOn(core)
    // strictly speaking it is not needed to have test->test here.
    // Cassandra module doesn't have tests, only integration tests
    // however, without it the generated pom.xml doesn't get this test dependencies
    .dependsOn(coreTest % "test->test;it->test")
    .dependsOn(testkit % "test->compile;it->compile")

// provides source providers for pekko-persistence-query
lazy val eventsourced =
  Project(id = "eventsourced", base = file("eventsourced"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(Dependencies.eventsourced)
    .settings(
      name := "pekko-projection-eventsourced")
    .dependsOn(core)
    .dependsOn(testkit % Test)

// provides offset storage backed by Kafka managed offset commits
lazy val kafka =
  Project(id = "kafka", base = file("kafka"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(Dependencies.kafka)
    .settings(
      name := "pekko-projection-kafka")
    .dependsOn(core)

lazy val kafkaTest =
  Project(id = "kafka-test", base = file("kafka-test"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.kafkaTest)
    .settings(
      name := "pekko-projection-kafka-test",
      publish / skip := true)
    .dependsOn(kafka)
    .dependsOn(testkit % Test)
    .dependsOn(slick % "test->test;it->it")

// provides source providers for durable state changes
lazy val `durable-state` =
  Project(id = "durable-state", base = file("durable-state"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(crossScalaVersions := Dependencies.Scala2And3Versions)
    .settings(Dependencies.state)
    .settings(
      name := "pekko-projection-durable-state")
    .dependsOn(core)
    .dependsOn(testkit % Test)

lazy val examples = project
  .configs(IntegrationTest.extend(Test))
  .settings(headerSettings(IntegrationTest))
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Defaults.itSettings)
  .settings(Dependencies.examples)
  .dependsOn(slick % "test->test")
  .dependsOn(jdbc % "test->test")
  .dependsOn(cassandra % "test->test;test->it")
  .dependsOn(eventsourced)
  .dependsOn(`durable-state`)
  .dependsOn(kafkaTest % "test->test")
  .dependsOn(testkit % Test)
  .settings(publish / skip := true, scalacOptions += "-feature", javacOptions += "-parameters")

lazy val docs = project
  .enablePlugins(PekkoParadoxPlugin, ParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(core, testkit)
  .settings(
    name := "Apache Pekko Projections",
    pekkoParadoxGithub := Some("https://github.com/apache/incubator-pekko-projection"),
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/pekko-projection/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/pekko-projection/${projectInfoVersion.value}",
    Compile / paradoxProperties ++= Map(
      "project.url" -> "https://pekko.apache.org/docs/pekko-projection/current/",
      "canonical.base_url" -> "https://pekko.apache.org/docs/pekko-projection/current",
      "github.base_url" -> "https://github.com/apache/incubator-pekko-projection",
      "pekko.version" -> Dependencies.Versions.pekko,
      // Pekko
      "extref.pekko.base_url" -> s"https://pekko.apache.org/docs/pekko/${Dependencies.PekkoVersionInDocs}/%s",
      "scaladoc.pekko.base_url" -> s"https://pekko.apache.org/api/pekko/${Dependencies.PekkoVersionInDocs}/",
      "javadoc.pekko.base_url" -> s"https://pekko.apache.org/japi/pekko/${Dependencies.PekkoVersionInDocs}/",
      "javadoc.pekko.link_style" -> "direct",
      // Java
      "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
      // Scala
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
      "scaladoc.pekko.projection.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.pekko.projection.base_url" -> ""),
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    paradoxRoots := List("index.html", "getting-started/event-generator-app.html"),
    apidocRootPackage := "org.apache.pekko")

lazy val root = Project(id = "projection", base = file("."))
  .aggregate(core, coreTest, testkit, jdbc, slick, cassandra, eventsourced, kafka, kafkaTest, `durable-state`, examples, docs)
  .settings(
    publish / skip := true,
    name := "pekko-projection-root")
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(SitePlugin, MimaPlugin)

// check format and headers
TaskKey[Unit]("verifyCodeFmt") := {
  javafmtCheckAll.all(ScopeFilter(inAnyProject)).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted Java code found. Please run 'javafmtAll' and commit the reformatted code")
  }
}

addCommandAlias("verifyCodeStyle", "headerCheckAll; verifyCodeFmt")
