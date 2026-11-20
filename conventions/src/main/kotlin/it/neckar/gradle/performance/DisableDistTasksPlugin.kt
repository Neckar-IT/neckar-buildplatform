package it.neckar.gradle.performance

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin

/**
 * Disables distribution tasks (`distTar`, `distZip`, `shadowDistTar`, `shadowDistZip`)
 * when the `application` and/or `shadow` plugins are applied.
 *
 * This plugin uses reactive hooks (`plugins.withType<>`) so it can be applied regardless
 * of plugin order and does not require the target plugins to already be present.
 *
 * Distribution archives are not needed in this monorepo — all services are deployed
 * via Docker/jib.
 */
class DisableDistTasksPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.withType(ApplicationPlugin::class.java) {
      target.tasks.named("distTar").configure { enabled = false }
      target.tasks.named("distZip").configure { enabled = false }
    }

    target.plugins.withType(ShadowPlugin::class.java) {
      target.plugins.withType(ApplicationPlugin::class.java) {
        target.afterEvaluate {
          tasks.named("shadowDistTar").configure { enabled = false }
          tasks.named("shadowDistZip").configure { enabled = false }
        }
      }
    }
  }
}
