### Added

- `SharedFileKind` in `it.neckar.buildplatform:projects` (package `it.neckar.projects.sharing`) and the helpers `Project.publishFiles(entry, artifacts)` and `Project.resolvedFiles(entry)` in `it.neckar.buildplatform:conventions`: a project publishes a file or directory as the consumable configuration `<entry>Elements` with `Usage`/`Category` `it.neckar.shared.<entry>`, another project resolves it through a project or coordinate dependency ([!21379](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21379))
- Artifacts `it.neckar.open.project-registry:open-projects` and `it.neckar.open.meistercharts.project-registry:meistercharts-projects` (Apache License 2.0), released with the build platform; `project(Projects.open.…)` inside `kotlin { sourceSets { } }` declares a missing project as the coordinate `it.neckar.open:<name>:<neckarOpenVersion>`, and `ConfiguredProject` takes an explicit `path` ([!21368](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21368))
- `ParsedPackageJson.dependencyNames`: every package of `dependencies` and `devDependencies`, whatever its version specifier ([!21367](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21367))
- `JsTestMode.NodeJsWithDom` for `executableJsApplication`: Mocha in Node.js with the DOM globals of `jsdom` 31.0.1 through `global-jsdom/register`; `JsTestMode.Disabled` is absent ([!21200](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21200))
- `projectsAccessor(GradleProjectPath)` in `it.neckar.buildplatform:projects` (`:open:commons:kotlin-lang` → `Projects.open.commons.kotlinLang`) and the parameter `isSubstituted` of `substituteNeckarCoordinatesWithSourceProjects`; `it.neckar.open.meistercharts.project-registry` counts as a group of an included build ([!20980](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20980))
- Plugin `it.neckar.base` (fat-jar metadata, capability conflicts, BOMs, repositories, Kotlin and Java compiler options, reproducible archives) and plugin `it.neckar.module-group` in `it.neckar.buildplatform:conventions`, plus the pnpm module conventions (`configurePnpmModule`), `configureNodeJsRootExtension`, `limitParallelNodeTasksAndKotlinJsLinks`, `substituteNeckarCoordinatesWithSourceProjects`, `GitProperty`, `declaresOrvalCodegen` and `CapabilityConflictPlugin` ([!20972](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20972))
- Artifact `it.neckar.buildplatform:conventions` with 17 convention plugins (`it.neckar.ksp.*`, `it.neckar.generation.types-list`, `it.neckar.openapi.generation-config`, `it.neckar.dependency-fence`, `it.neckar.open-module`, `it.neckar.npm-bundle`, `it.neckar.tailwind`, `it.neckar.generate-icons`, `it.neckar.no-inline-scripts`, `it.neckar.verify.main-class-exists`, …), the module conventions `configureJvmModule`, `configureKspProcessorModule`, `configureMultiplatformModule`, `configureDetekt`, `registerIntegrationTestSuite` and the build-script helpers; `config/detekt/detekt.yml` ships in the jar. The KSP conventions add the processors in the Gradle property `neckarOpenVersion` and fail with a message naming it when it is missing ([!20964](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20964))
- Artifact `it.neckar.buildplatform:ksp-contract`: `KspPluginOptions`, `KspProcessingParameterName` and the types-list model, moved from `internal/open/ksp`; every project of the build platform publishes signed to Maven Central ([!20964](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20964))
- Catalog plugin alias `dev-detekt` (`dev.detekt`, version ref `dev-detekt`) ([!20870](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20870))
- `externalizeInlineScripts(siteDirectory)`: moves every inline `<script>` of the HTML files below a directory into `inline-script-<sha256>.js`; fails on an inline `importmap` or `speculationrules` and on a classic inline script with `async`, `defer` or `integrity` ([!20763](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20763))

### Changed

- Catalog: `software.amazon.awssdk:s3` 2.59.3 ([!21315](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21315))
- Catalog: `org.controlsfx:controlsfx` 11.2.7 ([!21314](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21314))
- Catalog: `clikt` 5.2.1 ([!21313](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21313))
- Catalog: `clikt` 5.2.0 ([!21236](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21236))
- Catalog: plugin `com.autonomousapps.dependency-analysis` 3.20.4 ([!21235](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21235))
- Catalog: `netty` 4.2.21.Final ([!21234](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21234))
- Catalog: `javafx` 25.0.5.1 ([!21233](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21233))
- Catalog: `aws-sdk-kotlin` 1.9.38, `software.amazon.awssdk:s3` 2.59.2 ([!21232](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21232))
- `executableJsApplication` and `configureWasmJsTarget` start webpack-dev-server with `--host 127.0.0.1`; `executableJsApplication(devServerHost)` sets another address ([!21219](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21219))
- `detekt.yml` activates the rule `GitLabApiValueDefault` outside test sources ([!21153](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21153))
- Catalog: plugin `com.autonomousapps.dependency-analysis` 3.20.3 ([!21097](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21097))
- Catalog: `com.microsoft.playwright:playwright` 1.65.0 ([!21015](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21015))
- Catalog: `org.asciidoctor:asciidoctor5-jvm-core-plugin` 5.2.0 ([!21010](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21010))
- Catalog: `com.tngtech.keycloakmock:mock` 0.22.0 ([!21009](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21009))
- Catalog: `aws-sdk-kotlin` 1.9.37, `software.amazon.awssdk:s3` 2.59.1 ([!21008](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21008))
- Catalog: `org.jetbrains.kotlinx:kotlinx-io-core` 0.9.3 ([!21007](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21007))
- Catalog: `ch.qos.logback:logback-classic` 1.6.7 ([!21006](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21006))
- Catalog: `org.bouncycastle` 1.87 ([!20946](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20946))
- Catalog: `aws-sdk-kotlin` 1.9.35, `software.amazon.awssdk:s3` 2.58.0 ([!20945](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20945))
- Catalog: `io.kotest:kotest-property` 6.2.7 ([!20944](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20944))
- Catalog: `org.apache.commons:commons-lang3` 3.22.0 ([!20788](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20788))
- Catalog: `aws-sdk-kotlin` 1.9.33, `software.amazon.awssdk:s3` 2.57.12 ([!20785](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20785))
- `ArtifactKey` and `FileContentHash` wrap a `Sha256`; `ArtifactKey.parse(text)` reads the form `in-<sha256>`. `isProducedByRootBuild` excludes the group `it.neckar.thirdparty` (`ThirdPartyGroup`) and below ([!20773](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20773))
- POMs of `it.neckar.buildplatform` name https://github.com/Neckar-IT/neckar-buildplatform as project URL and SCM ([!20539](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/20539))

### Fixed

- `clean` of the pnpm project that owns the Node.js installation keeps `.gradle`, where CI points `GRADLE_USER_HOME`; deleting it removed the build cache under running tasks ([!21146](https://git.neckar.it/neckarit/neckar-hub/-/merge_requests/21146))
