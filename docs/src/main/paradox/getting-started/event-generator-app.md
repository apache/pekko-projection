<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# EventGeneratorApp

This is a simulation of fake Event Sourced shopping carts. The details of this implementation is not
important for understanding Projections. It's needed for @ref:[running the example](running.md).

Please look at the @extref:[Apache Pekko reference documentation for Event Sourcing](pekko:typed/persistence.html)
for how to implement real `EventSourcedBehavior`.

Add the `EventGeneratorApp` to your project:

Scala
:  @@snip [EventGeneratorApp.scala](/examples/src/test/scala/docs/guide/EventGeneratorApp.scala) { #guideEventGeneratorApp }

Java
:  @@snip [EventGeneratorApp.java](/examples/src/test/java/jdocs/guide/EventGeneratorApp.java) { #guideEventGeneratorApp }
