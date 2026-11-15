package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Calls [action] with the [Test] task of the unit test classes: `test` of a Kotlin/JVM module,
 * `jvmTest` of a Kotlin Multiplatform module with the JVM target `jvm`.
 */
fun Project.withUnitTestTask(action: (TaskProvider<Test>) -> Unit) {
  plugins.withId(BuildplatformPlugins.Java) {
    action(tasks.named<Test>("test"))
  }

  plugins.withId(BuildplatformPlugins.KotlinMultiPlatform) {
    the<KotlinMultiplatformExtension>().targets.matching { it.name == "jvm" }.configureEach {
      action(tasks.named<Test>("jvmTest"))
    }
  }
}

/**
 * Runs this task on the test classes and the classpath of [unitTest].
 *
 * Reads [unitTest] when this task is configured. A mapped [TaskProvider] would carry [unitTest] as
 * producer and make this task depend on it.
 */
fun Test.useTestClassesOf(unitTest: TaskProvider<Test>) {
  testClassesDirs = unitTest.get().testClassesDirs
  classpath = unitTest.get().classpath
}
