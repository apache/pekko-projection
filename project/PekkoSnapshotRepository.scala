import sbt.Keys._
import sbt._

/**
 * This plugins conditionally adds Akka snapshot repository.
 */
object PekkoSnapshotRepositories extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  // If using a snapshot version of either Pekko or Pekko Connectors, add both snapshot repos
  // in case there are transitive dependencies to other snapshot artifacts
  override def projectSettings: Seq[Def.Setting[_]] = {
    resolvers ++= (sys.props
      .get("build.pekko.version")
      .orElse(sys.props.get("build.connectors.kafka.version")) match {
      case Some(_) =>
        Seq(
          // akka and alpakka-kafka use Sonatype's snapshot repo
          "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/"))
      case None => Seq.empty
    })
  }
}
