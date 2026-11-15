package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Declares the `jvm` target.
 */
fun KotlinMultiplatformExtension.declareJvmTarget() {
  jvm {
  }
}

/**
 * Declares the `js` target: ES modules, Node.js/Mocha tests, and a webpack setup emitting a
 * modern module.
 */
fun KotlinMultiplatformExtension.declareJsTarget(project: Project) {
  js {
    useEsModules() //if enabled, "*.mjs" files are generated in build/compileSync/js/main/productionExecutable/kotlin

    //Only relevant if `binaries.executable()` is set in the project itself
    generateTypeScriptDefinitions()

    browser {
      // Disable JS browser tests - Karma cannot execute ES modules
      // JS tests run in Node.js with Mocha instead (see nodejs block below)
      configureJsKarma(disableTests = true)

      webpackTask {
        output.library = null //necessary when using modern-module
        output.libraryTarget = "modern-module"
      }

      commonWebpackConfig {
        //devtool = WebpackDevtool.SOURCE_MAP

        cssSupport {
          enabled = true //enable CSS support for all tasks (https://kotlinlang.org/docs/js-project-setup.html#building-executables)
        }

        //Required for "modern-module" support
        experiments.add("outputModule")
      }
    }

    // Enable Node.js tests with Mocha - Node.js supports ES modules natively
    // Note: Browser-specific APIs (Canvas, DOM) are not available in Node.js
    nodejs {
      testTask {
        useMocha()
      }
    }
  }

  //Configure JS Compile tasks to use ES2015 modules
  //It seems necessary to configure this *after* calling useEsModules() on the JS target (to avoid overwriting with "es5")
  project.tasks.withType<KotlinJsCompile>().configureEach {
    compilerOptions {
      // https://kotlinlang.org/docs/js-project-setup.html#support-for-es2015-features
      target = "es2015"
    }
  }
}

/** The property (`-PskipNativeReleaseLink`) that drops the release link of every Kotlin/Native executable from `assemble`. */
const val SkipNativeReleaseLinkProperty: String = "skipNativeReleaseLink"

/**
 * Declares the `linuxX64` target.
 *
 * Under `-PskipNativeReleaseLink` the release link task stays registered, so a script still builds it by name.
 */
fun KotlinMultiplatformExtension.declareLinuxX64Target(project: Project, skipPropertyLookup: SkipPropertyLookup = SkipPropertyLookup.GradlePropertyOrEnvironment) {
  val target = linuxX64()
  val skipNativeReleaseLink = project.isSkipRequested(SkipNativeReleaseLinkProperty, "assemble leaves out the release link of every Kotlin/Native executable", skipPropertyLookup)
  if (skipNativeReleaseLink.not()) return

  // Runs after the Kotlin plugin's binaries.all, so this assemble action follows the one that adds the link task provider.
  target.binaries.withType<Executable>().matching { it.buildType == NativeBuildType.RELEASE }.all {
    val releaseLinkTaskName = linkTaskName
    project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure {
      setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == releaseLinkTaskName })
    }
  }
}
