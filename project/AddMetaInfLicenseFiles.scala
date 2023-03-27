import sbt.AutoPlugin

import sbt.Keys._
import sbt._
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin.autoImport._

object AddMetaInfLicenseFiles extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = SonatypeApachePlugin

  override lazy val projectSettings = Seq(
    apacheSonatypeDisclaimerFile := Some((LocalRootProject / baseDirectory).value / "DISCLAIMER"))

}
