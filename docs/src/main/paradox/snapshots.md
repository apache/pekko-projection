---
project.description: Snapshot builds of Pekko Projection are provided via the Sonatype snapshot repository.
---
# Snapshots

[snapshots]:        https://repository.apache.org/content/groups/snapshots/org/apache/pekko/pekko-projection-core_2.13/

Snapshots are published to the Sonatype Snapshot repository after every successful build on master.
Add the following to your project build definition to resolve Pekko Projection's snapshots:

## Configure repository

Maven
:   ```xml
    <project>
    ...
      <repositories>
        <repository>
            <id>snapshots-repo</id>
            <name>Sonatype snapshots</name>
            <url>https://repository.apache.org/content/groups/snapshots/</url>
        </repository>
      </repositories>
    ...
    </project>
    ```

sbt
:   ```scala
    // sbt 1.9.0+
    resolvers += Resolver.ApacheMavenSnapshotsRepo
    // use the following if you are using an older version of sbt
    resolvers += "apache-snapshot-repository" at "https://repository.apache.org/content/repositories/snapshots"
    ```

Gradle
:   ```gradle
    repositories {
      maven {
        url  "https://repository.apache.org/content/groups/snapshots/"
      }
    }
    ```

## Documentation

The [snapshot documentation](https://pekko.apache.org/docs/pekko-projection/snapshot) is updated with every snapshot build.

## Versions

The snapshot repository is cleaned from time to time with no further notice. Check [Sonatype snapshots Pekko Projection files](https://repository.apache.org/content/groups/snapshots/org/apache/pekko/) to see what versions are currently available.
