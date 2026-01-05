/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import sbt._
import Keys.{ scalacOptions, _ }
import sbt.plugins.JvmPlugin

object PekkoDisciplinePlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin
  override lazy val projectSettings = disciplineSettings

  // allow toggling for pocs/exploration of ideas without discipline
  val enabled = !sys.props.contains("pekko.no.discipline")

  // We allow warnings in docs to get the 'snippets' right
  val nonFatalWarningsFor = Set("docs")

  lazy val disciplineSettings =
    if (enabled) {
      Seq(
        Compile / scalacOptions ++= (
          if (!nonFatalWarningsFor(name.value)) Seq("-Xfatal-warnings")
          else Seq.empty
        ),
        Test / scalacOptions --= testUndicipline,
        Compile / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 13)) =>
            disciplineScalacOptions ++ Set(
              "-Xlint:-strict-unsealed-patmat")
          case _ =>
            Nil
        }).toSeq,
        // Discipline is not needed for the docs compilation run (which uses
        // different compiler phases from the regular run), and in particular
        // '-Ywarn-unused:explicits' is an issue
        // https://github.com/akka/akka/issues/26119
        Compile / doc / scalacOptions --= disciplineScalacOptions.toSeq :+ "-Xfatal-warnings",
        // having discipline warnings in console is just an annoyance
        Compile / console / scalacOptions --= disciplineScalacOptions.toSeq)
    } else {
      Seq(Compile / scalacOptions += "-deprecation")
    }

  val testUndicipline = Seq("-Ywarn-dead-code" // '???' used in compile only specs
  )

  /**
   * Remain visibly filtered for future code quality work and removing.
   */
  val undisciplineScalacOptions = Set()

  /** These options are desired, but some are excluded for the time being */
  val disciplineScalacOptions = Set(
    "-Xfatal-warnings",
    "-feature",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-extra-implicit")
}
