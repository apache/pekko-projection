/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import sbt.Keys._
import sbt._

/**
 * This plugins conditionally adds Pekko snapshot repository.
 */
object PekkoSnapshotRepositories extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  // If using a snapshot version of either Pekko or Pekko Connectors, add both snapshot repos
  // in case there are transitive dependencies to other snapshot artifacts
  override def projectSettings: Seq[Def.Setting[_]] = {
    resolvers += Resolver.ApacheMavenSnapshotsRepo
  }
}
