package it.neckar.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Returns true if the opt-in `wasmJs` target is enabled for this build: `-PwasmJs=true`
 * (or `wasmJs=true` in gradle.properties).
 *
 * Default is OFF: the wasmJs target is perspective work that no product needs yet.
 * Disabled, the build skips all wasm tasks (~830 tasks / ~9% of the full build task graph).
 * The shared webMain source sets stay active - they are compiled and tested by the js target.
 */
fun Project.isWasmJsEnabled(): Boolean {
  return findProperty("wasmJs")?.toString().toBoolean()
}

/**
 * Declares the `wasmJs` browser target — but only when the build runs with `-PwasmJs=true`
 * (see [isWasmJsEnabled]), otherwise a no-op. A module reaches this by registering
 * [it.neckar.projects.KotlinTarget.WasmJs]; registering it keeps the target requirement from
 * cascading onto every multiplatform module in the build, because a wasmJs consumer requires
 * every commonMain dependency to also expose a wasmJs variant.
 *
 * Browser tests are disabled: the wasm test code is still compiled (which verifies wasmJs
 * source-compatibility), but not executed, so CI needs no browser to run the build.
 */
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.declareWasmJsTarget(project: Project) {
  if (project.isWasmJsEnabled().not()) {
    return
  }

  wasmJs {
    browser {
      testTask {
        enabled = false
      }
    }
  }
}

/**
 * Configures the module's own additions to the `wasmJs` target — a browser [executable] and
 * `wasmJsMain` [wasmJsMainDependencies]. A no-op when the target does not exist, so a module build
 * script never tests [isWasmJsEnabled] itself.
 *
 * Only for what a module needs on top of the shared setup; the target itself comes from the
 * registered [it.neckar.projects.KotlinTarget.WasmJs].
 */
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.configureWasmJsTarget(
  project: Project,
  /**
   * Whether to build a browser executable (webpack bundle) for the wasmJs target
   */
  executable: Boolean = false,
  /**
   * Dependencies for the wasmJsMain source set. Passed as lambda so the module build files
   * never have to test the wasmJs flag themselves - the source-set accessor only exists
   * when the target is registered.
   */
  wasmJsMainDependencies: (org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler.() -> Unit)? = null,
) {
  if (project.isWasmJsEnabled().not()) {
    return
  }

  wasmJs {
    if (executable) {
      binaries.executable()
    }
  }

  if (wasmJsMainDependencies != null) {
    sourceSets.getByName("wasmJsMain").dependencies(wasmJsMainDependencies)
  }
}
