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

ThisBuild / versionScheme := Some(VersionScheme.SemVerSpec)
sourceDistName := "apache-pekko-projection"
sourceDistIncubating := false

ThisBuild / resolvers += Resolver.ApacheMavenSnapshotsRepo
ThisBuild / reproducibleBuildsCheckResolver := Resolver.ApacheMavenStagingRepo

lazy val core =
  Project(id = "core", base = file("core"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.core)
    .settings(AutomaticModuleName.settings("pekko.projection.core"))
    .settings(name := "pekko-projection-core")
    .settings(Protobuf.settings)

lazy val coreTest =
  Project(id = "core-test", base = file("core-test"))
    .disablePlugins(MimaPlugin)
    .settings(Dependencies.coreTest)
    .settings(name := "pekko-projection-core-test")
    .settings(publish / skip := true)
    .dependsOn(core)
    .dependsOn(testkit % Test)

lazy val testkit =
  Project(id = "testkit", base = file("testkit"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.testKit)
    .settings(AutomaticModuleName.settings("pekko.projection.testkit"))
    .settings(name := "pekko-projection-testkit")
    .dependsOn(core)

// provides offset storage backed by a JDBC table
lazy val jdbc =
  Project(id = "jdbc", base = file("jdbc"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.jdbc)
    .settings(AutomaticModuleName.settings("pekko.projection.jdbc"))
    .settings(name := "pekko-projection-jdbc")
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

lazy val jdbcIntTest =
  Project(id = "jdbc-int-test", base = file("jdbc-int-test"))
    .disablePlugins(MimaPlugin)
    .settings(Dependencies.jdbc)
    .settings(
      name := "pekko-projection-jdbc-int-test",
      publish / skip := true,
      Test / parallelExecution := false)
    .dependsOn(jdbc)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides offset storage backed by a JDBC (Slick) table
lazy val slick =
  Project(id = "slick", base = file("slick"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(
      // Transitive dependency `scala-reflect` to avoid `NoClassDefFoundError`.
      // See: https://github.com/slick/slick/issues/2933
      libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        case _            => Nil
      }))
    .settings(Dependencies.slick)
    .settings(AutomaticModuleName.settings("pekko.projection.slick"))
    .settings(
      name := "pekko-projection-slick",
      versionScheme := None)
    .dependsOn(jdbc)
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

lazy val slickIntTest =
  Project(id = "slick-int-test", base = file("slick-int-test"))
    .disablePlugins(MimaPlugin)
    .settings(Dependencies.slick)
    .settings(
      name := "pekko-projection-slick-int-test",
      versionScheme := None,
      publish / skip := true,
      Test / parallelExecution := false)
    .dependsOn(slick)
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides offset storage backed by a Cassandra table
lazy val cassandra =
  Project(id = "cassandra", base = file("cassandra"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.cassandra)
    .settings(AutomaticModuleName.settings("pekko.projection.cassandra"))
    .settings(name := "pekko-projection-cassandra")
    .dependsOn(core)

lazy val cassandraTest =
  Project(id = "cassandra-test", base = file("cassandra-test"))
    .disablePlugins(MimaPlugin)
    .settings(Dependencies.cassandra)
    .settings(name := "pekko-projection-cassandra-test")
    .settings(publish / skip := true)
    .settings(Test / parallelExecution := false)
    .dependsOn(core)
    .dependsOn(cassandra)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides source providers for pekko-persistence-query
lazy val eventsourced =
  Project(id = "eventsourced", base = file("eventsourced"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.eventsourced)
    .settings(AutomaticModuleName.settings("pekko.projection.eventsourced"))
    .settings(name := "pekko-projection-eventsourced")
    .dependsOn(core)
    .dependsOn(testkit % Test)

// provides offset storage backed by Kafka managed offset commits
lazy val kafka =
  Project(id = "kafka", base = file("kafka"))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(Dependencies.kafka)
    .settings(AutomaticModuleName.settings("pekko.projection.kafka"))
    .settings(name := "pekko-projection-kafka")
    .dependsOn(core)

lazy val kafkaTest =
  Project(id = "kafka-test", base = file("kafka-test"))
    .configs(IntegrationTest)
    .enablePlugins(ReproducibleBuildsPlugin)
    .disablePlugins(MimaPlugin)
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
    .settings(Dependencies.state)
    .settings(AutomaticModuleName.settings("pekko.projection.durable-state"))
    .settings(name := "pekko-projection-durable-state")
    .dependsOn(core)
    .dependsOn(testkit % Test)

lazy val userProjects: Seq[ProjectReference] = List[ProjectReference](
  core, jdbc, slick, cassandra, eventsourced, kafka, `durable-state`, testkit)

lazy val examples = project
  .configs(IntegrationTest.extend(Test))
  .settings(headerSettings(IntegrationTest))
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(Defaults.itSettings)
  .settings(Dependencies.examples)
  .dependsOn(slick % "test->test")
  .dependsOn(jdbc % "test->test")
  .dependsOn(cassandraTest % "test->test")
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
    pekkoParadoxGithub := Some("https://github.com/apache/pekko-projection"),
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/pekko-projection/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/pekko-projection/${projectInfoVersion.value}",
    Compile / paradoxProperties ++= Map(
      "project.url" -> "https://pekko.apache.org/docs/pekko-projection/current/",
      "canonical.base_url" -> "https://pekko.apache.org/docs/pekko-projection/current",
      "github.base_url" -> "https://github.com/apache/pekko-projection",
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
    apidocRootPackage := "org.apache.pekko",
    Global / pekkoParadoxIncubatorNotice := None,
    Compile / paradoxMarkdownToHtml / sourceGenerators += Def.taskDyn {
      val targetFile = (Compile / paradox / sourceManaged).value / "license-report.md"

      (LocalRootProject / dumpLicenseReportAggregate).map { dir =>
        IO.copy(List(dir / "pekko-projection-root-licenses.md" -> targetFile)).toList
      }
    }.taskValue)

lazy val billOfMaterials = Project("bill-of-materials", file("bill-of-materials"))
  .enablePlugins(BillOfMaterialsPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .settings(
    name := "pekko-projection-bom",
    licenses := List(License.Apache2),
    bomIncludeProjects := userProjects,
    description := s"${description.value} (depending on Scala ${CrossVersion.binaryScalaVersion(scalaVersion.value)})")

lazy val root = Project(id = "projection", base = file("."))
  .aggregate(userProjects: _*)
  .aggregate(billOfMaterials, coreTest, kafkaTest, examples, docs)
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
