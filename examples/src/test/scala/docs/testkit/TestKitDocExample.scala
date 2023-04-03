/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.testkit

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.projection.ProjectionId
import pekko.projection.scaladsl.Handler
import pekko.projection.testkit.scaladsl.TestProjection
import pekko.projection.testkit.scaladsl.TestSourceProvider

//#testkit-import
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.projection.testkit.scaladsl.ProjectionTestKit

//#testkit-import

//#testkit-duration
import scala.concurrent.duration._

//#testkit-duration

//#testkit-testprojection
import org.apache.pekko.stream.scaladsl.Source

//#testkit-testprojection

// FIXME: this test can't run, most of its building blocks are 'fake' or are using null values
// abstract so it doesn run for now
abstract
//#testkit
class TestKitDocExample extends ScalaTestWithActorTestKit {
  private val projectionTestKit = ProjectionTestKit(system)

  // #testkit

  case class CartView(id: String)

  class CartViewRepository {
    def findById(id: String): Future[CartView] = Future.successful(CartView(id))
  }

  val cartViewRepository = new CartViewRepository

  // it only needs to compile
  val projection = TestProjection(ProjectionId("test", "00"), sourceProvider = null, handler = null)

  {
    // #testkit-run
    projectionTestKit.run(projection) {
      // confirm that cart checkout was inserted in db
      cartViewRepository.findById("abc-def").futureValue
    }
    // #testkit-run
  }

  {
    // #testkit-run-max-interval
    projectionTestKit.run(projection, max = 5.seconds, interval = 300.millis) {
      // confirm that cart checkout was inserted in db
      cartViewRepository.findById("abc-def").futureValue
    }
    // #testkit-run-max-interval
  }

  // #testkit-sink-probe
  projectionTestKit.runWithTestSink(projection) { sinkProbe =>
    sinkProbe.request(1)
    sinkProbe.expectNext(Done)
  }

  // confirm that cart checkout was inserted in db
  cartViewRepository.findById("abc-def").futureValue

  // #testkit-sink-probe

  {
    val handler: Handler[(Int, String)] = null

    // #testkit-testprojection
    val testData = Source((0, "abc") :: (1, "def") :: Nil)

    val extractOffset = (envelope: (Int, String)) => envelope._1

    val sourceProvider = TestSourceProvider(testData, extractOffset)

    val projection = TestProjection(ProjectionId("test", "00"), sourceProvider, () => handler)

    projectionTestKit.run(projection) {
      // assert logic ..
    }
    // #testkit-testprojection
  }
//#testkit
}
//#testkit
