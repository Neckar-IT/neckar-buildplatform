package it.neckar.gradle.npmbundle

import it.neckar.projects.GradleProjectPath
import org.gradle.api.Task
import org.gradle.api.tasks.PathSensitivity

/**
 * Declares that this task reads the npm package of [bundleProjectPath] through a `link:` dependency in
 * `package.json`, so the package exists before this task runs and a task with outputs runs again when it changes.
 */
fun Task.declaresNpmBundle(bundleProjectPath: GradleProjectPath) {
  dependsOn("${bundleProjectPath.path}:${NpmBundlePlugin.npmBundleTaskName}")

  inputs.dir(project.rootProject.project(bundleProjectPath.path).layout.buildDirectory.dir(NpmBundlePlugin.WorkingDirName))
    .withPropertyName("npmBundlePackage")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
