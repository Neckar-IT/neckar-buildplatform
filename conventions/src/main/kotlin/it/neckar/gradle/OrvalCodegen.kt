package it.neckar.gradle

import org.gradle.api.Task
import org.gradle.api.tasks.PathSensitivity

/**
 * Declares the OpenAPI specs of [backendModulePaths] as input and `build/generated/open-api` as output of the Orval run inside this
 * task, so a backend API change, a `clean` or a cache hit regenerates the TypeScript types. The `dependsOn` on each `openapiSpec`
 * stays in the consuming build script.
 */
fun Task.declaresOrvalCodegen(vararg backendModulePaths: String) {
  require(backendModulePaths.isNotEmpty()) {
    "Expected at least one backend module path for the Orval codegen inputs of <$path>"
  }

  val openapiSpecDirs = backendModulePaths.map { backendModulePath ->
    project.rootProject.project(backendModulePath)
      .layout.buildDirectory.dir("generated/openapi")
  }

  inputs.files(openapiSpecDirs)
    .withPropertyName("backendOpenApiSpecs")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  outputs.dir(project.layout.buildDirectory.dir("generated/open-api"))
    .withPropertyName("orvalGeneratedTypes")
}
