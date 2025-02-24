# Release Notes (1.1.x)

## 1.1.0
Release notes for Apache Pekko Projections 1.1.0. See [GitHub Milestone for 1.1.0-M1](https://github.com/apache/pekko-projection/milestone/1?closed=1) and [GitHub Milestone for 1.1.0](https://github.com/apache/pekko-projection/milestone/2?closed=1) for a fuller list of changes.

It is recommended to use Pekko 1.1 jars with this release and with pekko-projection-eventsourced, you require pekko-persistence 1.1.3 and above.

### Breaking Changes
* pekko-projection-eventsourced: Configuring persistence plugins at runtime for EventSourcedBehavior ([PR225](https://github.com/apache/pekko-projection/pull/225)) (not in 1.1.0-M1)
    * If you use pekko-projection-eventsourced, this change requires pekko-persistence 1.1.3 and above.

### Additions
* Scala 3 support for pekko-projection-slick
    * Minimum of Scala 3.3.0 required
    * Slick has been upgraded to v3.5.1
* pekko-projections-bom added ([PR155](https://github.com/apache/pekko-projection/pull/155))

### Depenendency Upgrades
* jackson 2.17.3
