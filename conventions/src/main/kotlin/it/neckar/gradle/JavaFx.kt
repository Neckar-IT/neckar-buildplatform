package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.openjfx.gradle.JavaFXModule

/**
 * Configures JavaFX for this project
 */
fun Project.configureJavaFX(modules: List<JavaFXModule> = listOf(JavaFXModule.CONTROLS)) {
  plugins.apply(BuildplatformPlugins.Javafx)

  val javaFxOptions: org.openjfx.gradle.JavaFXOptions = extensions.getByType(org.openjfx.gradle.JavaFXOptions::class.java)

  val javaPluginExtension = extensions.getByType(JavaPluginExtension::class.java)
  val languageVersion = javaPluginExtension.toolchain.languageVersion.get()

  javaFxOptions.version = languageVersion.asInt().toString()
  javaFxOptions.modules = modules.map { it.moduleName }
}
