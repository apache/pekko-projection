import org.apache.pekko.projections.Dependencies

ThisBuild / resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

lazy val core =
  Project(id = "core", base = file("core"))
    .configs(IntegrationTest)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.core)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-core",
      Compile / packageBin / packageOptions += Package.ManifestAttributes(
        "Automatic-Module-Name" -> "pekko.projection.core"))
    .settings(Protobuf.settings)

lazy val coreTest =
  Project(id = "core-test", base = file("core-test"))
    .configs(IntegrationTest)
    .settings(headerSettings(IntegrationTest))
    .disablePlugins(MimaPlugin)
    .settings(Defaults.itSettings)
    .settings(Dependencies.coreTest)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-core-test")
    .settings(publish / skip := true)
    .dependsOn(core)
    .dependsOn(testkit % Test)

lazy val testkit =
  Project(id = "testkit", base = file("testkit"))
    .configs(IntegrationTest)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.testKit)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-testkit")
    .dependsOn(core)

// provides offset storage backed by a JDBC table
lazy val jdbc =
  Project(id = "jdbc", base = file("jdbc"))
    .configs(IntegrationTest.extend(Test))
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.jdbc)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-jdbc")
    .dependsOn(core)
    .dependsOn(coreTest % "test->test")
    .dependsOn(testkit % Test)

// provides offset storage backed by a JDBC (Slick) table
lazy val slick =
  Project(id = "slick", base = file("slick"))
    .configs(IntegrationTest.extend(Test))
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.slick)
    .settings(MetaInfLicenseNoticeCopy.settings)
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
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.cassandra)
    .settings(MetaInfLicenseNoticeCopy.settings)
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
    .settings(Dependencies.eventsourced)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-eventsourced")
    .dependsOn(core)
    .dependsOn(testkit % Test)

// provides offset storage backed by Kafka managed offset commits
lazy val kafka =
  Project(id = "kafka", base = file("kafka"))
    .configs(IntegrationTest)
    .settings(headerSettings(IntegrationTest))
    .settings(Defaults.itSettings)
    .settings(Dependencies.kafka)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-kafka")
    .dependsOn(core)
    .dependsOn(testkit % Test)
    .dependsOn(slick % "test->test;it->it")

// provides source providers for durable state changes
lazy val `durable-state` =
  Project(id = "durable-state", base = file("durable-state"))
    .configs(IntegrationTest)
    .settings(Dependencies.state)
    .settings(MetaInfLicenseNoticeCopy.settings)
    .settings(
      name := "pekko-projection-durable-state")
    .dependsOn(core)
    .dependsOn(testkit % Test)
    .settings(
      // no previous artifact so must disable MiMa until this is released at least once.
      mimaPreviousArtifacts := Set.empty)

lazy val examples = project
  .configs(IntegrationTest.extend(Test))
  .settings(headerSettings(IntegrationTest))
  .disablePlugins(MimaPlugin)
  .settings(Defaults.itSettings)
  .settings(Dependencies.examples)
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(slick % "test->test")
  .dependsOn(jdbc % "test->test")
  .dependsOn(cassandra % "test->test;test->it")
  .dependsOn(eventsourced)
  .dependsOn(`durable-state`)
  .dependsOn(kafka % "test->test")
  .dependsOn(testkit % Test)
  .settings(publish / skip := true, scalacOptions += "-feature", javacOptions += "-parameters")

lazy val docs = project
  .enablePlugins(ParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(core, testkit)
  .settings(
    name := "Apache Pekko Projections",
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/akka-projection/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/akka-projection/${projectInfoVersion.value}",
    Compile / paradoxProperties ++= Map(
      "project.url" -> "https://doc.akka.io/docs/akka-projection/current/",
      "canonical.base_url" -> "https://doc.akka.io/docs/akka-projection/current",
      "github.base_url" -> "https://github.com/apache/incubator-pekko-projection",
      "akka.version" -> Dependencies.Versions.pekko,
      // Akka
      "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.PekkoVersionInDocs}/%s",
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.PekkoVersionInDocs}/",
      "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.PekkoVersionInDocs}/",
      "javadoc.akka.link_style" -> "direct",
      // Alpakka
      "extref.alpakka.base_url" -> s"https://doc.akka.io/docs/alpakka/${Dependencies.ConnectorsVersionInDocs}/%s",
      "scaladoc.akka.stream.alpakka.base_url" -> s"https://doc.akka.io/api/alpakka/${Dependencies.ConnectorsVersionInDocs}/",
      "javadoc.akka.stream.alpakka.base_url" -> "",
      // Alpakka Kafka
      "extref.alpakka-kafka.base_url" -> s"https://doc.akka.io/docs/alpakka-kafka/${Dependencies.ConnectorsKafkaVersionInDocs}/%s",
      "scaladoc.akka.kafka.base_url" -> s"https://doc.akka.io/api/alpakka-kafka/${Dependencies.ConnectorsKafkaVersionInDocs}/",
      "javadoc.akka.kafka.base_url" -> "",
      // Java
      "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
      // Scala
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
      "scaladoc.akka.projection.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.akka.projection.base_url" -> "", // no Javadoc is published
      // Misc
      "extref.samples.base_url" -> "https://developer.lightbend.com/start/?group=akka&amp;project=%s",
      "extref.platform-guide.base_url" -> "https://developer.lightbend.com/docs/akka-platform-guide/%s"),
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    paradoxRoots := List("index.html", "getting-started/event-generator-app.html"),
    ApidocPlugin.autoImport.apidocRootPackage := "akka",
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifacts += (makeSite.value -> "www/"),
    publishRsyncHost := "akkarepo@gustav.akka.io",
    apidocRootPackage := "org.apache.pekko")

lazy val root = Project(id = "projection", base = file("."))
  .aggregate(core, coreTest, testkit, jdbc, slick, cassandra, eventsourced, kafka, `durable-state`, examples, docs)
  .settings(publish / skip := true)
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
