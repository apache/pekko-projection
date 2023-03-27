addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.7.0")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("org.mdedetrich" % "sbt-apache-sonatype" % "0.1.6")

// Documentation
// We have to deliberately use older versions of sbt-paradox because current Pekko sbt build
// only loads on JDK 1.8 so we need to bring in older versions of parboiled which support JDK 1.8
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.9.2")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-apidoc" % "0.10.1")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-project-info" % "2.0.0")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-dependencies" % "0.2.2")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
