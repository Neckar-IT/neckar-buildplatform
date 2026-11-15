# Build-Plattform

```bash
./gradlew -p internal/buildplatform build   # ohne den Root-Build
tools/bin/nit-release buildplatform 1.0.0   # Release-MR buildplatform | Release buildplatform-1.0.0
```

Die Build-Plattform ist die unterste veröffentlichte Ebene des Monorepos (ADL 0231) und ein eigener Gradle-Build, weil sie Build-Plugins liefert (ADL 0228). Der Root-Build bindet sie per `includeBuild("internal/buildplatform")` ein; sie baut auch ohne den Root-Build. Die Build-Plattform enthält die Projects der Maven-Gruppe `it.neckar.buildplatform`:

- `version-catalog`: den einzigen Versionskatalog des Repositories, [`internal/buildplatform/gradle/libs.versions.toml`](gradle/libs.versions.toml).
- `projects`: die Typen jeder Registry von Projekten (`ConfiguredProject`, `GradleProjectPath`, `ProjectDependencies`, …). Die Registry der Open Projects liegt in [`internal/open/project-registry`](../open/project-registry), die der MeisterCharts-Projects in [`internal/open/meistercharts/project-registry`](../open/meistercharts/project-registry), die des Monorepos in [`build-logic/registry`](../../build-logic/registry).
- `generated-file-marker`: den Marker generierter Dateien (ADL 0180).
- `ksp-contract`: die Namen der KSP-Optionen (`KspPluginOptions`) und das Modell der Types-Liste, die die KSP-Konventionen schreiben und die Prozessoren unter [`internal/open/ksp`](../open/ksp) lesen.
- `conventions`: die Convention-Plugins ohne Betriebswissen (`it.neckar.ksp.*`, `it.neckar.npm-bundle`, `it.neckar.tailwind`, `it.neckar.dependency-fence`, …), die Konventionen eines Moduls (`configureJvmModule`, `configureKspProcessorModule`, `configureMultiplatformModule`, `configureDetekt`, `registerIntegrationTestSuite`) und die Helfer der Build-Scripts (`addKotlinDependencies`, `declareJsTarget`, `configureJunit`, …). Die Detekt-Konfiguration [`config/detekt/detekt.yml`](config/detekt/detekt.yml) liegt als Resource im Jar. Ein Build außerhalb des Monorepos setzt die Gradle-Property `neckarOpenVersion`; in dieser Version fügen die Konventionen die KSP-Prozessoren und das Detekt-Rule-Set `it.neckar.open:detekt` hinzu.

`projects`, `generated-file-marker`, `ksp-contract` und `conventions` kompiliert die Build-Plattform mit Gradles embedded Kotlin, wie `build-logic`. Jedes Project erscheint mit dem Release der Build-Plattform signiert auf Maven Central, in der Version aus [`buildplatform.version`](buildplatform.version); `build.gradle.kts` setzt Version, Signatur und POM für jedes Project.

| Element | Wert |
|---|---|
| Artefakt | `it.neckar.buildplatform:version-catalog`, dieselben Einträge wie `internal/buildplatform/gradle/libs.versions.toml`, ohne deren Kommentare |
| Version | [`internal/buildplatform/buildplatform.version`](buildplatform.version), unabhängig von `monorepo.version`; der Release-Commit setzt die Release-Version, der Commit danach die nächste `-SNAPSHOT`-Version |
| Release | `productDeclaration { }` und `delivery { }` stehen in [`internal/closed/buildplatform-deployment/build.gradle.kts`](../closed/buildplatform-deployment/build.gradle.kts), weil die Build-Plattform `build-logic` nicht auf dem Classpath hat; der Tag `buildplatform-<X.Y.Z>` startet `📦 Publish buildplatform to Maven Central` in [`gitlab-ci.d/tag.yml`](../../gitlab-ci.d/tag.yml) |
| Technische Notes | `internal/buildplatform/releases/buildplatform-<X.Y.Z>/`, daraus erzeugt `:internal:closed:buildplatform-deployment:generateReleaseNotes` die `internal/buildplatform/CHANGELOG.md` |
| Builds im Repository, die den Versionskatalog als `libs` laden | `internal/buildplatform` per Gradle-Konvention; das Root-`settings.gradle.kts`, `build-logic/settings.gradle.kts`, `internal/open/project-registry/settings.gradle.kts`, `internal/open/meistercharts/project-registry/settings.gradle.kts` über `from(files(…))`; der Fixture-Build von `functionalTest` in `build-logic/plugins/build.gradle.kts` |
| Leser außerhalb | ein standalone-fähiges Projekt über `from("it.neckar.buildplatform:version-catalog:$buildplatformVersion")`; den Wert schreibt [`tools/standalone-build/export-standalone.sh`](../../tools/standalone-build/export-standalone.sh) aus der obersten Release-Überschrift von [`internal/buildplatform/CHANGELOG.md`](CHANGELOG.md) |

Credentials und Signatur der Veröffentlichung: [maven-central-publishing.md](../open/docs/maven-central-publishing.md#credentials-in-gradlegradleproperties-eintragen).

## GitHub-Mirror

```bash
git clone https://github.com/Neckar-IT/neckar-buildplatform.git && cd neckar-buildplatform && ./gradlew build
```

https://github.com/Neckar-IT/neckar-buildplatform spiegelt jede Datei, die git unter `internal/buildplatform` führt, samt dem Build-Setup, das `tools/standalone-build/export-standalone.sh` für einen Build außerhalb des Monorepos schreibt, etwa Gradle Wrapper und `gradle.properties`. Das Repository ist read-only; Änderungen gehen in dieses Monorepo.

Entscheidung: ADL 0236.

| Branch oder Tag | Inhalt | Schreiber |
|---|---|---|
| `main` | das neueste Release, vor dem Push von `tools/standalone-build/verify-standalone.sh` gebaut | `🏷️ Publish buildplatform Release to GitHub` bei jedem Tag `buildplatform-*` |
| Tag `buildplatform-*` | der Stand des gleichnamigen Tags im Monorepo | `🏷️ Publish buildplatform Release to GitHub` |
| `develop` | der aktuelle Stand von `main` des Monorepos, ungebaut | `⏲ publish the open sources to GitHub`, täglich um 04:00 im Schedule `Publish Sonatype Snapshots / Publish Meistercharts to GitHub / Publish Open to GitHub` |

Die GitHub Action [`.github/workflows/build.yml`](.github/workflows/build.yml) baut `main` und jeden Tag `buildplatform-*` mit `./gradlew build`. Ein roter Lauf auf `main` öffnet ein Issue in neckar-hub; die Prüfungen vor jedem Push nennt der Header von [`gitlab-ci.d/scripts/publish-sources-to-github.sh`](../../gitlab-ci.d/scripts/publish-sources-to-github.sh).
