# Release Notes

## 1.0.0
Apache Pekko Projections 1.0.0 is based on Akka Projections 1.2.5. Pekko came about as a result of Lightbend's decision to make future
Akka releases under a [Business Software License](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka),
a license that is not compatible with Open Source usage.

Apache Pekko has changed the package names, among other changes. Config names have changed to use `pekko` instead
of `akka` in their names. Users switching from Akka to Pekko should read our [Migration Guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html).

Generally, we have tried to make it as easy as possible to switch existing Akka based projects over to using Pekko.

We have gone through the code base and have tried to properly acknowledge all third party source code in the
Apache Pekko code base. If anyone believes that there are any instances of third party source code that is not
properly acknowledged, please get in touch.

### Bug Fixes
We haven't had to fix any significant bugs that were in Akka Projections 1.2.5.

### Additions
* Scala 3 support
    * Minimum of Scala 3.3.0 required
    * pekko-projection-slick does not yet support Scala 3 (Slick does not yet have a full release that supports Scala 3)

### Dependency Upgrades
We have tried to limit the changes to third party dependencies that are used in Pekko Projections 1.0.0. These are some exceptions:

* jackson 2.14.3
* scalatest 3.2.14. Pekko users who have existing tests based on Akka Testkit may need to migrate their tests due to the scalatest upgrade. The [scalatest 3.2 release notes](https://www.scalatest.org/release_notes/3.2.0) have a detailed description of the changes needed.
* some of the database driver jars used in tests were upgraded due to security issues in the old jars
