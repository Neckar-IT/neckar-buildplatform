package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

/**
 * Configures the Node.js of the Kotlin/JS and Kotlin/Wasm targets of every project from the `engines` of the root `package.json`, and
 * runs the npm install of the Kotlin/JS root project with `--legacy-peer-deps`.
 */
fun Project.configureNodeJsRootExtension() {
  allprojects {
    project.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
      project.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version = npmVersion("node")
    }

    // The default Node.js of the wasmJs target links against libatomic.so.1, which the CI runner image lacks.
    project.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
      project.the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().version = npmVersion("node")
    }
  }

  // A build of subproject tasks alone installs Node.js per subproject, while the root :kotlinNpmInstall runs the one of the root.
  tasks.matching { it.name == "kotlinNpmInstall" }.configureEach {
    dependsOn(tasks.matching { it.name == "kotlinNodeJsSetup" })
  }

  // react-json-view 1.21.3 of the Lizergy planner-ui declares React <= 17 as peer; the export installs alike, its lockfile is cut from this one.
  rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>()
      .npmInstallTaskProvider
      .configure { args.add("--legacy-peer-deps") }
  }
}
