package it.neckar.gradle

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger

/** The neckar-rules rule set every Detekt run loads, from `internal/open/detekt`. */
private val DetektRules: MavenCoordinate = MavenCoordinate("it.neckar.open", "detekt")

/** The classpath resource holding the Detekt configuration of the build platform, `config/detekt/detekt.yml`. */
private const val DetektConfigResource: String = "it/neckar/gradle/detekt/detekt.yml"

/**
 * Configures Detekt for this module: the configuration files and the version of the neckar-rules rule set come from [settings],
 * [additionalConfig] sets the sources and runs last.
 */
fun Project.configureDetekt(settings: ModuleConventionSettings, additionalConfig: DetektExtension.() -> Unit) {
  // Auto-detect whether Detekt's bundled kotlin-compiler matches the project's Kotlin
  // major.minor. When it doesn't (e.g. Detekt 2.0.0-alpha.3 ships kotlin-compiler 2.3.21
  // while the project is on Kotlin 2.4), Detekt's type resolution is unreliable and its
  // findings can be false positives. We then downgrade Detekt to non-blocking: findings
  // still appear in the GitLab Code Quality widget, but the build is not failed.
  // Heals itself automatically once a Detekt release matching the project Kotlin is
  // available.
  //
  // The detekt configuration is populated by the Detekt Gradle plugin asynchronously,
  // so the lookup must be lazy (Provider). The provider is materialised when
  // failOnSeverity / the doLast listener actually read it.
  val detektBundledKotlinProvider: Provider<String> = providers.provider {
    detectDetektBundledKotlinVersion(this) ?: "unknown"
  }
  // Active Detekt version on the `detekt` configuration (e.g. `"2.0.0-alpha.5"`). Lookup is the
  // same lazy-provider pattern as the bundled-kotlin detection above.
  val detektVersionProvider: Provider<String> = providers.provider {
    detectDetektVersion(this) ?: "unknown"
  }
  // Versions that ship a matching kotlin-compiler (so the major.minor check would say "compatible")
  // but have other defects that make strict mode produce a wall of false positives. Per-version
  // override forces non-blocking regardless of the compatibility check. The list is curated by
  // hand — Renovate bumps to a later version drop out of the list automatically and re-enable
  // strict mode via the regular auto-detection. Re-evaluate each entry when a Detekt release
  // claims to fix the underlying defect.
  val detektStrictModeBlockedProvider: Provider<Boolean> = detektVersionProvider.map { it in DetektStrictModeBlockedVersions }
  val detektCompatibleProvider: Provider<Boolean> =
    detektBundledKotlinProvider.zip(detektVersionProvider) { bundled, detektVersion ->
      detektStrictModeEnabled(
        detektVersion = detektVersion,
        bundledKotlinVersion = bundled,
        languageVersion = KotlinSettings.languageVersionAsString,
      )
    }

  extensions.getByType(DetektExtension::class.java).apply {
    config.from(provider { settings.detektConfigFiles(this@configureDetekt) })

    // Kept at Detekt's own default (false): Detekt's per-module multithreading (Analyzer
    // dispatches one analysis task per source file onto a thread pool) races on the Kotlin
    // compiler's non-thread-safe KotlinCliJavaFileManagerImpl during type resolution. Two
    // worker threads concurrently mutating its internal Object2ObjectOpenHashMap corrupt the
    // map and crash with `ArrayIndexOutOfBoundsException` at rehash() — intermittent,
    // file-independent, and unrelated to any rule finding. Observed on `2.0.0-alpha.5`.
    // Upstream root cause lives in the Kotlin compiler's file manager, so no Detekt release
    // fixes it; turning off Detekt's intra-module parallelism removes the concurrent access.
    // Cross-module parallelism (Gradle running each module's Detekt task in parallel under
    // `org.gradle.parallel`) is unaffected and still provides the bulk of the speedup.
    // Refs: detekt#5403, detekt#2629.
    parallel = false
    buildUponDefaultConfig = true
    //autoCorrect = true

    // Two-tier policy:
    //   - severity: error   -> blocks the merge
    //   - severity: warning -> visible in GitLab Code Quality widget, does NOT block
    // Rules use the default severity (warning) unless explicitly bumped to error in
    // detekt.yml for correctness-critical findings (Tenant isolation, Flow.toList, etc.).
    //
    // Switched to `Never` when Detekt's bundled compiler does not match the project's Kotlin
    // version (auto-detection above) OR when the active Detekt version is on the explicit
    // blocklist (DetektStrictModeBlockedVersions — for Detekt releases that match the
    // Kotlin major.minor but produce a wall of false positives anyway).
    failOnSeverity.set(detektCompatibleProvider.map { if (it) FailOnSeverity.Error else FailOnSeverity.Never })

    // Detekt report paths are made repo-relative on the analysis tasks themselves, by UNSETTING the
    // absolute basePath there — not by setting it here. See the `basePath.unsetConvention()` in the
    // tasks.withType<Detekt> block below (build-cache relocatability); Detekt 2.0's reporters
    // already emit repo-relative paths without an explicit basePath.

    // Per-module baseline for pre-existing findings. The baseline is committed next to
    // build.gradle.kts; regenerate with `./gradlew :module:path:detektBaselineMain` after
    // intentionally changing rule behaviour. Rules themselves stay active — the baseline
    // only records what is known, new violations still fail detekt.
    baseline = project.file("detekt-baseline.xml")

    additionalConfig()
  }

  // Register the project-specific neckar-rules rule set so its RuleSetProvider
  // is on the detekt classpath. Skip the detekt module itself to avoid a self-dep.
  if (mavenModule != DetektRules.module) {
    dependencies.add("detektPlugins", DetektRules.withDefaultVersion(settings.detektRuleSetVersion(this)).toString())
    // detekt-gitlab-report adds an extra Code-Climate-format JSON next to the
    // existing reports. The MR pipeline merges all per-module JSONs and ships
    // them via `artifacts.reports.codequality` so GitLab's Code Quality widget
    // can show Detekt findings in MR diffs.
    dependencies.add("detektPlugins", "com.gitlab.cromefire:detekt-gitlab-report:0.5.0")
  }

  plugins.withType(dev.detekt.gradle.plugin.DetektPlugin::class) {
    tasks.withType(dev.detekt.gradle.Detekt::class) {
      val taskName = name

      // Relocatable build cache. Detekt declares `basePath` as an `@Input` String and its
      // Gradle plugin defaults it (convention) to the ABSOLUTE root project directory. That absolute
      // path is the ONLY non-relocatable input in the entire Detekt cache key — every other input is
      // normalized relocatably (source RELATIVE, config/baseline NONE, classpath CLASSPATH). Verified
      // with `-Dorg.gradle.caching.debug`: the sole `Appending input value fingerprint for 'basePath'`
      // line carries the checkout path. Each CI runner checks the repo out to a different absolute path
      // (worker-01/-02 × concurrency, no pinned GIT_CLONE_PATH), so the absolute basePath makes every
      // runner compute a different key and never reuse another runner's cached Detekt output — while
      // the fully-relocatable compile tasks hit the cache 100 %.
      //
      // Unsetting basePath removes it from the key (a null @Optional input contributes a constant
      // marker on every machine). Detekt 2.0's report renderers already emit repo-relative finding
      // paths without an explicit basePath (verified: checkstyle/SARIF/Code-Climate all render
      // `internal/…/Foo.kt`), so the GitLab Code Quality widget still aligns findings with the MR diff.
      // Net effect: Detekt output is shared across runners exactly like the compile output.
      //
      // Order matters: this is a `withType` configuration action on tasks the Detekt plugin has
      // already registered — and already given its basePath convention — so clearing the convention
      // here is what takes effect. A convention applied *after* this point would silently restore the
      // absolute path (and with it the cache miss), so verify with `-Dorg.gradle.caching.debug` when
      // upgrading Detekt.
      basePath.unsetConvention()

      // Generated code (e.g. createVersionConstants, KSP outputs, from-buildSrc copies) is
      // not authored by hand — applying lint rules to it produces noise without actionable
      // findings. The auto-registered detekt<SourceSet> tasks include srcDirs that ARE
      // generated dirs (e.g. registered via `kotlin.srcDir(generatorTask)`), so a relative
      // glob like `**/build/generated/**` does not match — the generated dir is itself the
      // source root. Filter via absolute path instead.
      exclude { it.file.absolutePath.replace('\\', '/').contains("/build/generated/") }

      // Detekt 2.0 swallows Kotlin compiler errors that occur during type resolution and
      // still reports the task as successful with 0 findings. This silently destroys the
      // entire type-resolution-dependent rule set (Tenant isolation, Flow.toList, neckar-
      // custom-rules) without any visible signal. Failure path observed: enabling a Kotlin
      // preview feature sets IS_COMPILED_IN_PRE_RELEASE_MODE on every output class file;
      // Detekt's embedded Kotlin compiler then refuses to load these classes during type
      // resolution and emits one compiler error per reference (tens of thousands per
      // module), but never fails the task.
      //
      // We parse the standard Detekt output line ("There were N compiler errors found
      // during analysis") via a logging listener and act on it ourselves.
      //
      // Strict vs. soft behaviour is decided automatically: if Detekt's bundled kotlin-
      // compiler matches the project's Kotlin major.minor, the count signals a real
      // problem and fails the task. If the versions differ (e.g. Detekt 2.0.0-alpha.3
      // bundles kotlin-compiler 2.3.21 while the project is on Kotlin 2.4), the count is
      // almost certainly a Detekt-version-mismatch false positive; we log a warning
      // instead of failing so the build is not blocked by Detekt's lag. Real Detekt
      // findings (via `failOnSeverity = Error`) block in strict mode alone.
      val compilerErrorCount = AtomicInteger(0)
      val compilerErrorPattern = Regex("There were (\\d+) compiler errors found during analysis")
      val outputListener = org.gradle.api.logging.StandardOutputListener { output ->
        compilerErrorPattern.find(output)?.let { match ->
          compilerErrorCount.set(match.groupValues[1].toInt())
        }
      }
      logging.addStandardOutputListener(outputListener)
      logging.addStandardErrorListener(outputListener)
      val console = ansiConsole
      doLast {
        val count = compilerErrorCount.get()
        if (count > 0) {
          val baseMessage = "Detekt analysis encountered $count Kotlin compiler errors during type resolution. " +
            "This invalidates all type-resolution-dependent rules and produces unreliable findings. " +
            "Re-run with `--debug` or set `detekt { debug = true }` to see the underlying errors."
          if (detektCompatibleProvider.get()) {
            throw GradleException(baseMessage)
          } else {
            val downgradeReason = if (detektStrictModeBlockedProvider.get()) {
              "Detekt ${console.orange(detektVersionProvider.get())} is on DetektStrictModeBlockedVersions: " +
                "its analysis reports compiler errors that make every type-resolution rule unreliable"
            } else {
              "Detekt bundles kotlin-compiler ${console.orange(detektBundledKotlinProvider.get())}, " +
                "project uses Kotlin ${console.orange(KotlinSettings.languageVersionAsString)}"
            }
            logger.warn(
              "${console.yellow(baseMessage)} $downgradeReason — " +
                "${console.yellow("compile-error fail-check downgraded to warning")}, and findings are not blocking " +
                "(failOnSeverity = ${console.orange("Never")}). Findings still appear in the GitLab Code Quality widget " +
                "for inspection. Tracking: https://github.com/detekt/detekt/issues/7531"
            )
          }
        }
      }

      reports {
        html.required.set(true)
        // The Checkstyle report (Detekt's XML format) is consumed by reports.neckar.it for
        // finding-count aggregation on the index page.
        checkstyle.required.set(true)
        // SARIF is published alongside HTML/XML so machine consumers (IDE plugins, dashboards)
        // can ingest findings without scraping the HTML.
        sarif.required.set(true)
        // Custom Code-Climate JSON report from detekt-gitlab-report. The reportId
        // 'gitlab' is defined by that plugin. Path matches the convention used by
        // other Detekt reports (build/reports/detekt/<task-name>.<ext>) so the
        // CI `after_script` glob picks it up alongside the rest.
        custom {
          reportId = "gitlab"
          outputLocation.set(layout.buildDirectory.file("reports/detekt/$taskName.gitlab.json"))
        }
      }
    }
  }

  //Remove detekt from check task
  tasks.named("check") {
    this.setDependsOn(this.dependsOn.filterNot {
      it is TaskProvider<*> && it.name.contains("detekt")
    })
  }
}

/**
 * Returns the Kotlin compiler version (e.g. `"2.3.21"`) bundled with the Detekt CLI on the
 * project's `detekt` configuration classpath, or `null` if it cannot be determined.
 *
 * Resolved by inspecting the artifact file names — robust against future Detekt repackaging
 * because every Detekt release ships `kotlin-compiler-<version>.jar` as a runtime dependency.
 */
private fun detectDetektBundledKotlinVersion(project: Project): String? {
  val detektConfig = project.configurations.findByName("detekt") ?: return null
  return try {
    detektConfig.incoming.artifactView { lenient(true) }.files
      .firstOrNull { it.name.matches(KotlinCompilerJarPattern) }
      ?.name
      ?.let { KotlinCompilerJarPattern.matchEntire(it)?.groupValues?.get(1) }
  } catch (e: Exception) {
    project.logger.debug("Could not resolve Detekt's bundled kotlin-compiler version: ${e.message}")
    null
  }
}

/**
 * Decides whether Detekt runs in strict (merge-blocking) mode: the active Detekt version must not be
 * on [DetektStrictModeBlockedVersions], and the Kotlin compiler it bundles must match the
 * language version the repository compiles with — an analyzer built against a different compiler
 * reports resolution errors that are artefacts of the mismatch rather than real findings.
 */
internal fun detektStrictModeEnabled(detektVersion: String?, bundledKotlinVersion: String?, languageVersion: String): Boolean {
  if (detektVersion in DetektStrictModeBlockedVersions) {
    return false
  }
  return isMajorMinorEqual(bundledKotlinVersion, languageVersion)
}

/**
 * Compares two Kotlin version strings on major.minor only (e.g. `"2.3.21"` and `"2.3"` match;
 * `"2.3.21"` and `"2.4"` do not). Returns `false` if either input is `null` or malformed.
 */
internal fun isMajorMinorEqual(a: String?, b: String?): Boolean {
  fun majorMinor(v: String?): Pair<Int, Int>? {
    val parts = v?.split('.')?.take(2) ?: return null
    if (parts.size < 2) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    return major to minor
  }
  return majorMinor(a) != null && majorMinor(a) == majorMinor(b)
}

internal val KotlinCompilerJarPattern = Regex("""kotlin-compiler-(\d+\.\d+(?:\.\d+)?)\.jar""")

/**
 * Detekt versions whose standalone analyzer produces a wall of false positives despite a matching
 * bundled `kotlin-compiler` major.minor. Listed versions are pinned to non-blocking mode regardless
 * of [isMajorMinorEqual]; any version not listed runs through the regular compatibility check.
 *
 * Curated by hand — newer Renovate-driven bumps fall out automatically and re-enable strict mode
 * unless they're explicitly added here. Re-evaluate each entry against the next Detekt release.
 *
 * workaround: dependency=libs:dev-detekt:2.0.0-alpha.6 observed-on=2026-11-03 upstream=https://github.com/detekt/detekt/issues/7531
 *
 *  - `2.0.0-alpha.5`: bundles `kotlin-compiler:2.4.0` (would match Kotlin 2.4 → strict), but Detekt's
 *    standalone analyzer can't load Kotlin compiler plugins, so every file calling the
 *    kotlinx-serialization-plugin-generated `Type.serializer()` emits "unresolved reference
 *    'serializer'" compiler errors that trip the strict compile-error fail-check. Repo-wide
 *    `:detektAll --continue` sweep on alpha.4 (same code path) surfaced ~30
 *    affected files across 12 modules, plus 3 pre-existing PrintStackTrace findings that would
 *    become blocking. Alternatives investigated (per-file exclusions, content-based filter,
 *    `enableCompilerPlugin = true`) either don't scale or hide rule coverage on serialization-heavy
 *    modules.
 *  - `2.0.0-alpha.6`: same root cause as alpha.5, re-evaluated against this release and still
 *    unfixed. Bundles `kotlin-compiler:2.4.10` (matches Kotlin 2.4 → would go strict). Upstream
 *    detekt#9218 landed in the 2.0.0 milestone but only
 *    ports detekt to the Kotlin 2.4 Analysis API; it does not teach the standalone analyzer to load
 *    compiler plugins, and the alpha.6 release notes contain no compiler-plugin entry. Measured on
 *    this bump: `:internal:patterns:ktor-backend:detektMain` reports 13221 compiler
 *    errors during analysis and fails with 32 issues, while the identical source tree on alpha.5
 *    passes. Per the fail-check's own contract those findings are unreliable — the compiler errors
 *    invalidate every type-resolution-dependent rule — so they are the wall of false positives this
 *    list exists for, not a real regression to fix.
 */
internal val DetektStrictModeBlockedVersions = setOf(
  "2.0.0-alpha.5",
  "2.0.0-alpha.6",
)

/**
 * Returns the active Detekt version (e.g. `"2.0.0-alpha.5"`) on the project's `detekt` configuration,
 * or `null` if it cannot be determined. Resolved from the `detekt-api` artifact file name, which —
 * unlike the fat `detekt-cli-<version>-all.jar` — carries the plain version without an `-all` suffix.
 */
private fun detectDetektVersion(project: Project): String? {
  val detektConfig = project.configurations.findByName("detekt") ?: return null
  return try {
    detektConfig.incoming.artifactView { lenient(true) }.files
      .firstOrNull { it.name.matches(DetektApiJarPattern) }
      ?.name
      ?.let { DetektApiJarPattern.matchEntire(it)?.groupValues?.get(1) }
  } catch (e: Exception) {
    project.logger.debug("Could not resolve Detekt version: ${e.message}")
    null
  }
}

// The version group requires a leading digit so the related `detekt-api-test-fixtures-<version>.jar`
// artifact (which shares the `detekt-api-` prefix) is not mistaken for the plain api jar.
internal val DetektApiJarPattern = Regex("""detekt-api-(\d[^/]*)\.jar""")

/**
 * The Detekt configuration of the build platform, unpacked from the classpath of the conventions into the project cache directory
 * (`.gradle` of the root project), which `clean` leaves alone; a configuration cache entry keeps naming an existing file.
 * Unpacked again when the content differs, e.g. after an update of the conventions.
 */
fun Project.buildplatformDetektConfig(): File {
  val content = ModuleConventionSettings::class.java.classLoader.getResourceAsStream(DetektConfigResource)?.use { it.readBytes() }
    ?: throw GradleException("The conventions of the build platform lack the resource $DetektConfigResource")
  val projectCacheDirectory: File = gradle.startParameter.projectCacheDir ?: rootDir.resolve(".gradle")
  val target = projectCacheDirectory.resolve("buildplatform/detekt/detekt.yml")
  if (target.isFile && target.readBytes().contentEquals(content)) {
    return target
  }
  target.parentFile.mkdirs()
  // Projects configure in parallel; a reader sees the old or the new file, never a half-written one.
  val temporary = File.createTempFile("detekt", ".yml", target.parentFile)
  temporary.writeBytes(content)
  Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  return target
}
