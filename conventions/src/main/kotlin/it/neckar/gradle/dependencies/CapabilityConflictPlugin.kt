package it.neckar.gradle.dependencies

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradlex.jvm.dependency.conflict.detection.JvmDependencyConflictDetectionPlugin

/**
 * Fails the resolution of a configuration holding two coordinates of one known capability, such as `bcprov-jdk15to18` and `bcprov-jdk18on`.
 * The resolution plugin of the same library would pick the highest version instead (`docs/gradle/daemon-memory.md`).
 *
 * kotaml replaces kaml wherever both meet: a library such as `io.ktor:ktor-openapi-schema` still depends on kaml, whose classes kotaml
 * ships under the same package, so a fat jar would hold every class twice.
 */
class CapabilityConflictPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(JvmDependencyConflictDetectionPlugin::class.java)
    target.dependencies.modules.module(KamlModule) {
      replacedBy(KotamlModule, "kaml is archived; kotaml is its maintained fork with the same packages")
    }
  }

  companion object {
    const val KamlModule: String = "com.charleskorn.kaml:kaml"

    const val KotamlModule: String = "io.heapy.kotaml:kotaml"
  }
}
