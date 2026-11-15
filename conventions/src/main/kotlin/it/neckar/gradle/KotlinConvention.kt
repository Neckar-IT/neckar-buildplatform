package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Whether `-Xenhanced-coroutines-debugging` is enabled for this build, controlled by the
 * `-PenhancedCoroutinesDebugging` Gradle property. Present (any value other than `false`) enables it;
 * absent disables it. Off by default — see [KotlinSettings.additionalFreeCompilerArgsJVM].
 */
fun Project.isEnhancedCoroutinesDebuggingEnabled(): Boolean =
  providers.gradleProperty("enhancedCoroutinesDebugging").orNull?.let { it != "false" } ?: false

/**
 * Configures the Kotlin compiler options for a project — JVM, multiplatform, KSP processor alike.
 *
 * The `KotlinJvmCompile` / `KotlinJsCompile` blocks match no task in a module without the
 * corresponding target, so one function serves every target combination.
 */
fun Project.configureKotlin() {
  //Ensure that this is only called once for each project
  require(project.extra.has("kotlinConfigured").not()) {
    "Kotlin already configured for project ${project.path}. Do *not* call configureKotlin() multiple times"
  }
  project.extra["kotlinConfigured"] = true

  // JS-only projects ("kotlin-js" plugin) are unrepresentable since Kotlin 2.4
  // (KotlinJsProjectExtension removed). No runtime guard needed.

  tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompile> {
    compilerOptions.freeCompilerArgs.addAllDistinct(KotlinSettings.freeCompilerArgs)
    compilerOptions.freeCompilerArgs.add(explicitReturnTypesArg())
  }

  tasks.withType<KotlinJvmCompile> {
    compilerOptions.freeCompilerArgs.addAllDistinct(KotlinSettings.freeCompilerArgs + KotlinSettings.additionalFreeCompilerArgsJVM(project.isEnhancedCoroutinesDebuggingEnabled()))
    compilerOptions.freeCompilerArgs.add(explicitReturnTypesArg())
    compilerOptions.jvmDefault = JvmDefaultMode.NO_COMPATIBILITY //default methods for interfaces
  }

  tasks.withType<KotlinJsCompile> {
    compilerOptions.freeCompilerArgs.addAllDistinct(KotlinSettings.freeCompilerArgs + KotlinSettings.additionalFreeCompilerArgsJS)
    compilerOptions.freeCompilerArgs.add(explicitReturnTypesArg())
  }

  //for common
  this.extensions.findByType<KotlinProjectExtension>()?.applyKotlinConfiguration()

  //For JVM projects
  extensions.findByType<KotlinJvmProjectExtension>()?.applyJvmKotlinConfiguration()
}

private fun Task.explicitReturnTypesArg(): String =
  KotlinSettings.explicitReturnTypesArg(KotlinSettings.CompilationScope.ofCompileTask(name))

/**
 * Adds all elements that are not already in the list
 */
private fun ListProperty<String>.addAllDistinct(elements: List<String>) {
  val newElements = get().toMutableSet()
  newElements.addAll(elements)
  set(newElements)
}

/**
 * Applies the annotations for the experimental features we are using from Kotlin
 */
fun KotlinProjectExtension.applyKotlinConfiguration() {
  sourceSets.all {
    KotlinSettings.optInExperimentalAnnotations.forEach {
      languageSettings.optIn(it)
    }

    languageSettings {
      progressiveMode = true
      languageVersion = KotlinSettings.languageVersionAsString
      apiVersion = KotlinSettings.apiVersionAsString
    }
  }
}

/**
 * Applies the kotlin configuration to the JVM target
 */
fun KotlinJvmProjectExtension.applyJvmKotlinConfiguration() {
  compilerOptions {
    languageVersion = KotlinSettings.languageVersion
    apiVersion = KotlinSettings.apiVersion
    progressiveMode = true
    optIn = KotlinSettings.optInExperimentalAnnotations
    javaParameters = true
    suppressWarnings = false
    extraWarnings = true
  }

  applyKotlinConfiguration()
}

/**
 * Applies the shared configuration for multiplatform projects.
 *
 * Declares no target: which platforms a module builds for is its registered target set
 * (`multiplatform(path, …)` in the project registry), applied by `declareTarget` per entry.
 */
fun KotlinMultiplatformExtension.applyMultiplatformKotlinConfiguration() {
  applyKotlinConfiguration()

  compilerOptions {
    suppressWarnings = false
    extraWarnings = true
  }
}
