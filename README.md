# Apache Pekko Projections

Apache Pekko Projections provides an abstraction for consuming a stream of `Envelope` (where `Envelope` contains a payload and a trackable offset). This streams can originate from persisted events, Kafka topics,
or other Apache Pekko connectors. 

Apache Pekko Projections also provides tools to track, restart and distribute these projections.

## Documentation

Apache Pekko Projections are documented at https://pekko.apache.org/docs/pekko-projection/current/.

To keep up with the latest releases check out [Pekko Projections releases](https://pekko.apache.org/docs/pekko-projection/current/release-notes.html).

## Community

You can join these forums and chats to discuss and ask Pekko and Pekko connector related questions:

- [GitHub discussions](https://github.com/apache/pekko-projection/discussions): for questions and general discussion.
- [Pekko users mailing list](https://lists.apache.org/list.html?users@pekko.apache.org): for Pekko usage discussions.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/pekko-projection/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

## Contributing

Contributions are very welcome. If you have an idea on how to improve Pekko, don't hesitate to create an issue or submit a pull request.

Run `sbt applyCodeStyle` to format code and `sbt checkCodeStyle` to check it. Scala formatting explicitly
follows Pekko core's import-selector layout, ready for the separate Scalafix OrganizeImports migration.
Java formatting uses JDK 17 and preserves import ordering. The generated `ProjectionMessages.java`
is excluded from Java formatting.

## License

Apache Pekko Projections is available under the Apache License, version 2.0. See [LICENSE](https://github.com/apache/pekko-projection/blob/main/LICENSE) file for details.

## Organizing Scala imports

Run `sbt sortImports` on the default Scala 2.13 build to organize imports with
Scalafix and then format Scala sources. OrganizeImports resolves relative imports
without expanding them, keeps `org.apache.pekko` and `pekko.*` together, and removes
unused imports using compiler diagnostics. Unused-import diagnostics are enabled
only while SemanticDB is enabled; ordinary compilation keeps its existing warning policy.
Scalafmt no longer sorts imports independently. Run the alias from the build root;
it explicitly includes the `jdbc-int-test`, `slick-int-test`, `grpc-int-test`, `r2dbc-int-test`
projects which are not part of the root's normal aggregation.

For a single module, run `sbt scalafixEnable "<module> / Compile / scalafix OrganizeImports"`
(and the corresponding `Test` task as needed), followed by `sbt scalafmtAll`.
Use `scalafixAll --check OrganizeImports` after `scalafixEnable` for a read-only check
of the root aggregation; run the same task scoped to each additional project to check those too.
These semantic tasks compile their inputs, so they require the same dependencies
and generated sources as a normal build.

Run `scripts/check-organize-imports.sh` for a small isolated regression test of
import grouping, relative Pekko imports, unused-import removal, compilation and
idempotence. An alternative sbt launcher can be passed as the command arguments.
The rule configuration and regression fixture are adapted from
[apache/pekko#3342](https://github.com/apache/pekko/pull/3342).

Sources containing documentation snippet markers (`// #...` or `//#...`) are
excluded from Scalafix in both Compile and Test. Import rewriting can move these
markers or remove imports that the extracted documentation needs. Keep those
imports organized manually while preserving snippet boundaries. These files
remain included in normal compilation and formatting.
