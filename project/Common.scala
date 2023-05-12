import org.apache.pekko.projections.Dependencies
import sbtdynver.DynVerPlugin.autoImport._
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import com.typesafe.tools.mima.plugin.MimaKeys._
import sbtdynver.DynVerPlugin

object Common extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = JvmPlugin && SonatypeApachePlugin && DynVerPlugin

  override def globalSettings =
    Seq(
      startYear := Some(2022),
      // apiURL defined in projectSettings because version.value is not correct here
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/apache/incubator-pekko-projection"),
          "git@github.com:apache/incubator-pekko-projection.git")),
      developers += Developer(
        "contributors",
        "Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/incubator-pekko-projection/graphs/contributors")),
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
      (ThisBuild / baseDirectory).value.toString,
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "main" else s"v${version.value}"
        s"https://github.com/apache/incubator-pekko-projection/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
      },
      "-skip-packages",
      "org.apache.pekko.pattern" // for some reason Scaladoc creates this
    ),
    autoAPIMappings := true,
    apiURL := Some(url(s"https://pekko.apache.org/api/pekko-projection/${projectInfoVersion.value}")),
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    // -a Show stack traces and exception class name for AssertionErrors.
    // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
    // -q Suppress stdout for successful tests.
    Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v", "-q"),
    Test / logBuffered := false,
    // temporarily disable mima checks
    mimaPreviousArtifacts := Set.empty)

  override lazy val buildSettings = Seq(
    dynverSonatypeSnapshots := true)

}
