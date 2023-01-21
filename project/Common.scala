import akka.projections.Dependencies
import com.geirsson.CiReleasePlugin
import sbtdynver.DynVerPlugin.autoImport._
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import com.typesafe.tools.mima.plugin.MimaKeys._
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object Common extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = JvmPlugin && CiReleasePlugin

  override def globalSettings =
    Seq(
      organization := "org.apache.pekko",
      organizationName := "Apache Software Foundation",
      organizationHomepage := Some(url("https://www.apache.org/")),
      startYear := Some(2022),
      homepage := Some(url("https://pekko.apache.org/")),
      // apiURL defined in projectSettings because version.value is not correct here
      scmInfo := Some(
          ScmInfo(url("https://github.com/apache/incubator-pekko-projection"), "git@github.com:apache/incubator-pekko-projection.git")),
      developers += Developer(
          "contributors",
          "Contributors",
          "dev@pekko.apache.org",
          url("https://github.com/apache/incubator-pekko-projection/graphs/contributors")),
      licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),
      description := "Apache Pekko Projection.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    crossVersion := CrossVersion.binary,
    crossScalaVersions := Dependencies.ScalaVersions,
    scalaVersion := Dependencies.Scala213,
    javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation"),
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
        "-doc-title",
        "Apache Pekko Projection",
        "-doc-version",
        version.value,
        "-sourcepath",
        (baseDirectory in ThisBuild).value.toString,
        "-doc-source-url", {
          val branch = if (isSnapshot.value) "main" else s"v${version.value}"
          s"https://github.com/apache/incubator-pekko-projection/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
        },
        "-skip-packages",
        "akka.pattern" // for some reason Scaladoc creates this
      ),
    scalafmtOnCompile := true,
    autoAPIMappings := true,
    apiURL := Some(url(s"https://doc.akka.io/api/akka-projection/${projectInfoVersion.value}")),
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    // -a Show stack traces and exception class name for AssertionErrors.
    // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
    // -q Suppress stdout for successful tests.
    Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v", "-q"),
    Test / logBuffered := false,
    // temporarily disable mima checks
    mimaPreviousArtifacts := Set.empty,
    sonatypeProfileName := "org.apache.pekko")

}
