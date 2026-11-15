package it.neckar.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask
import org.jetbrains.kotlin.gradle.tasks.CompileUsingKotlinDaemon
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilerExecutionStrategy
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * What every project shares in the root build of the monorepo and in the export of `internal/open`:
 * fat-jar metadata, capability conflicts, the BOMs, Maven Central and the repository of detekt-gitlab-report, the Kotlin and Java
 * compiler options and reproducible archives.
 * The build applying it sets `group` ([mavenGroupOf]) and `version` itself.
 */
class NeckarBasePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      // Acts once a project applies the Shadow plugin.
      plugins.apply(BuildplatformPlugins.FatJarMetadata)
      plugins.apply(BuildplatformPlugins.CapabilityConflicts)

      // The native QUIC jars of netty-codec-http3 collide in every fat jar; no module enables HTTP/3.
      configurations.configureEach {
        exclude(group = "io.netty", module = "netty-codec-native-quic")
      }

      // KotlinPackageJsonTask writes the version into package.json without declaring it, so a version bump left a stale lockfile.
      tasks.withType<KotlinPackageJsonTask>().configureEach {
        inputs.property("projectVersion", provider { project.version.toString() })
      }

      // Enforce consistent versions via BOMs across all modules.
      alignVersionsThroughPlatforms()

      repositories.mavenCentral()
      // The Detekt conventions load detekt-gitlab-report, which only this GitLab Maven repository serves; filtered to that module.
      repositories.maven("https://gitlab.com/api/v4/projects/25796063/packages/maven") {
        name = "GitLab Detekt Report Repo"
        content { includeModule("com.gitlab.cromefire", "detekt-gitlab-report") }
      }

      if (System.getProperty("org.gradle.debug") != null) {
        logger.lifecycle("DEBUG MODE DETECTED - running kotlin compiler in process")
        tasks.withType<CompileUsingKotlinDaemon>().configureEach {
          compilerExecutionStrategy.set(KotlinCompilerExecutionStrategy.IN_PROCESS)
        }
      }

      configureKotlinCompilerOptions()
      configureJavaCompilerOptions()

      // Without these flags every jar/zip/tar embeds the local file modification times and a
      // filesystem-dependent entry order, so two builds of the same commit produce different bytes.
      tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
      }
    }
  }

  private fun Project.configureKotlinCompilerOptions() {
    // The common code of multiplatform projects, compiled to the metadata format.
    tasks.withType<KotlinCommonCompile>().configureEach {
      compilerOptions {
        languageVersion.set(KotlinSettings.languageVersion)
        apiVersion.set(KotlinSettings.apiVersion)
        allWarningsAsErrors.set(false)
        // Warnings are reported, never suppressed: a `true` here emits -nowarn and silences the whole target.
        suppressWarnings.set(false)
        verbose.set(false)
        freeCompilerArgs.addAll(KotlinSettings.freeCompilerArgs)
      }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
      val enhancedCoroutinesDebugging = project.isEnhancedCoroutinesDebuggingEnabled()
      compilerOptions {
        languageVersion.set(KotlinSettings.languageVersion)
        apiVersion.set(KotlinSettings.apiVersion)
        freeCompilerArgs.addAll(KotlinSettings.freeCompilerArgs + KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging))
        allWarningsAsErrors.set(false)
        suppressWarnings.set(false)
        verbose.set(false)
        // jvmTarget follows the Java toolchain.
        javaParameters.set(true)
      }
    }

    tasks.withType<KotlinJsCompile>().configureEach {
      compilerOptions {
        languageVersion.set(KotlinSettings.languageVersion)
        apiVersion.set(KotlinSettings.apiVersion)
        freeCompilerArgs.addAll(KotlinSettings.freeCompilerArgs + KotlinSettings.additionalFreeCompilerArgsJS)
      }
    }
  }

  private fun Project.configureJavaCompilerOptions() {
    tasks.withType<JavaCompile>().configureEach {
      options.compilerArgs = listOf(
        "-Xlint:-serial", // no warning for a missing serialVersionUID
        "-parameters", // the names of method parameters in the .class files
      )
      options.encoding = "UTF8"
      options.isWarnings = true
    }
  }
}
