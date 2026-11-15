package it.neckar.gradle

import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

/**
 * Configures the Kotlin JS target to produce an executable JS browser application.
 * This application can be embedded using a simple script tag in an HTML file.
 *
 * The main function is called when the application is loaded.
 */
fun KotlinJsTargetDsl.executableJsApplication(
  /**
   * The name of the variable that is used to access the library in the browser.
   * Is used to register the library in the global scope.
   */
  varName: String,

  /**
   * The JS target type, ES2015 by default.
   */
  jsTargetType: JsTargetType = JsTargetType.ES2015,

  /**
   * The module type that is used for the webpack configuration.
   */
  webpackModuleType: WebpackModuleType = WebpackModuleType.Window,

  /**
   * The module type that is used for the webpack configuration for dev.
   */
  webpackModuleTypeForDev: WebpackModuleType = WebpackModuleType.Window,

  /**
   * Controls test execution for projects using ES modules (ModernModule).
   * - [JsTestMode.Disabled]: Tests are disabled
   * - [JsTestMode.NodeJs]: Tests run in Node.js with Mocha (supports ES modules natively)
   */
  testMode: JsTestMode = JsTestMode.NodeJs,

  /**
   * Optional configuration for the webpack task.
   * Can be used to add additional configuration to the webpack task.
   *
   * Use with care!
   * It should not be necessary in most cases
   */
  additionalRunTaskWebpackConfig: KotlinWebpack.() -> Unit = {},
) {
  require(varName.isNotBlank()) {
    "varName must not be blank"
  }

  binaries.executable()

  val usesEsModules = webpackModuleType == WebpackModuleType.ModernModule || webpackModuleTypeForDev == WebpackModuleType.ModernModule

  browser {
    runTask {
      webpackModuleType.configure(this, varName)
      additionalRunTaskWebpackConfig()
    }

    webpackTask {
      webpackModuleType.configure(this, varName)
    }

    // Disable browser tests for ES modules - Karma cannot execute ESM
    // For browser-specific tests (Canvas, DOM), use WebpackModuleType.Window instead
    testTask {
      enabled = false
    }

    // Enable outputModule experiment for webpack when using ES modules
    if (usesEsModules) {
      commonWebpackConfig {
        experiments.add("outputModule")
      }
    }
  }

  // Configure Node.js tests when NodeJs test mode is selected
  if (testMode == JsTestMode.NodeJs) {
    nodejs {
      testTask {
        useMocha()
      }
    }
  }

  run {
    val tasks = project.tasks

    //These tasks are only available if binaries.executable() is called in the project itself
    val jsBrowserProductionWebpack = tasks.findByName("jsBrowserProductionWebpack").requireNotNull() as KotlinWebpack
    val jsBrowserDevelopmentWebpack = tasks.findByName("jsBrowserDevelopmentWebpack").requireNotNull() as KotlinWebpack
    val jsDevelopmentExecutableCompileSync = tasks.findByName("jsDevelopmentExecutableCompileSync").requireNotNull()
    val jsProductionExecutableCompileSync = tasks.findByName("jsProductionExecutableCompileSync").requireNotNull()
    val jsBrowserDevelopmentRun = tasks.findByName("jsBrowserDevelopmentRun").requireNotNull() as KotlinWebpack

    // workaround: dependency=gradle:wrapper:9.7.1 observed-on=2026-10-07
    //Both compile sync tasks write build/js/packages/<module>/kotlin, so each webpack task reads the output of the other
    //variant too. Without these edges Gradle 9.7.1 fails `build jsBrowserDevelopmentWebpack` with "Property has implicit dependency".
    jsBrowserProductionWebpack.mustRunAfter(jsDevelopmentExecutableCompileSync)
    jsBrowserProductionWebpack.mustRunAfter(jsProductionExecutableCompileSync)

    jsBrowserDevelopmentWebpack.mustRunAfter(jsDevelopmentExecutableCompileSync)
    jsBrowserDevelopmentWebpack.mustRunAfter(jsProductionExecutableCompileSync)

    jsBrowserDevelopmentRun.mustRunAfter(jsDevelopmentExecutableCompileSync)
    jsBrowserDevelopmentRun.mustRunAfter(jsProductionExecutableCompileSync)

    webpackModuleType.configure(jsBrowserProductionWebpack, varName)
    webpackModuleTypeForDev.configure(jsBrowserDevelopmentWebpack, varName)
  }

  project.tasks.withType<KotlinJsCompile>().configureEach {
    compilerOptions {
      target = jsTargetType.value
    }
  }

  // Register a convenient 'run' task as alias for jsBrowserProductionRun
  if (project.tasks.findByName("run") == null) {
    project.tasks.register("run") {
      group = "application"
      description = "Runs the JS browser application in production mode (alias for jsBrowserProductionRun)"
      dependsOn("jsBrowserProductionRun")
    }
  }
}

/**
 * Controls how tests are executed for JS projects using ES modules (ModernModule).
 *
 * ## Important Limitation
 * Browser tests (Karma) are NOT compatible with ES modules (ModernModule) because:
 * 1. Karma cannot execute ESM syntax
 * 2. The Kotlin/JS plugin automatically enables `outputModule` webpack experiment
 *
 * For projects needing browser-specific tests (Canvas, DOM), use [WebpackModuleType.Window]
 * instead of [WebpackModuleType.ModernModule].
 */
enum class JsTestMode {
  /**
   * Tests are disabled. Use this for backwards compatibility or when tests are not needed.
   */
  Disabled,

  /**
   * Tests run in Node.js using Mocha.
   * Node.js has native ES module support, making it ideal for testing ES module code.
   *
   * Note: Browser-specific APIs (Canvas, DOM, localStorage, etc.) are NOT available.
   * For Canvas tests, consider using the `canvas` npm package as a polyfill.
   */
  NodeJs,
}

/**
 * The target for [KotlinJsCompile] tasks
 */
enum class JsTargetType(val value: String) {
  /**
   * This is the default value
   */
  ES5("es5"),

  /**
   * Should provide additional features:
   * https://kotlinlang.org/docs/js-project-setup.html#support-for-es2015-features
   */
  ES2015("es2015"),
}

enum class WebpackModuleType {
  Var {
    override fun configure(kotlinWebpack: KotlinWebpack, varName: String) {
      kotlinWebpack.output.library = varName
      kotlinWebpack.output.libraryTarget = "var"
      kotlinWebpack.devtool = "source-map"
    }
  },

  /**
   * Seems to work fine, when running a Kotlin application that is just initialized/started by a single JS statement
   */
  Window {
    override fun configure(kotlinWebpack: KotlinWebpack, varName: String) {
      kotlinWebpack.output.library = varName
      kotlinWebpack.output.libraryTarget = "window"
      kotlinWebpack.devtool = "source-map"
    }
  },
  Umd {
    override fun configure(kotlinWebpack: KotlinWebpack, varName: String) {
      kotlinWebpack.output.library = varName
      kotlinWebpack.output.libraryTarget = "umd"
      kotlinWebpack.devtool = "source-map"
    }
  },
  Umd2 {
    override fun configure(kotlinWebpack: KotlinWebpack, varName: String) {
      kotlinWebpack.output.library = varName
      kotlinWebpack.output.libraryTarget = "umd2"
      kotlinWebpack.devtool = "source-map"
    }
  },

  /**
   * Seems to work when using the generated code as TypeScript module.
   * Note: This requires experiments.outputModule to be enabled in commonWebpackConfig
   */
  ModernModule {
    override fun configure(kotlinWebpack: KotlinWebpack, varName: String) {
      kotlinWebpack.output.library = null // do *not* set a library name
      kotlinWebpack.output.libraryTarget = "modern-module"
      kotlinWebpack.devtool = "source-map"
    }
  },

  ;

  abstract fun configure(kotlinWebpack: KotlinWebpack, varName: String)
}
