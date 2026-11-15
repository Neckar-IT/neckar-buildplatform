package it.neckar.gradle.verify

import it.neckar.gradle.VerificationGroup
import it.neckar.gradle.findNamed
import it.neckar.gradle.requireDirExists
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

/**
 * Verifies that the main class exists.
 * Extracts the configured main class from the [JavaApplication] plugin.
 */
class VerifyMainClassExistsPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    val extension = target.extensions.create<VerifyMainClassExistsExtension>(ExtensionName).apply {
      mainClassName.convention(target.provider {
        //Find the main class from the application plugin
        val javaApplication = target.extensions.findByType(JavaApplication::class.java)
        resolveMainClassNameToVerify(target.path, javaApplication?.mainClass?.orNull)
      })

      classFilesDir.convention(target.provider {
        val extension = target.extensions.getByType<KotlinJvmProjectExtension>()
        val mainSourceSet = extension.sourceSets["main"]
        mainSourceSet.kotlin.destinationDirectory.get()
      })

    }

    val verifyMainClassExists = target.tasks.register<VerifyMainClassExistsTask>(VerifyMainClassExistsTaskName) {
      group = VerificationGroup
      description = "Verifies that the main class exists"

      dependsOn("compileKotlin")

      this.mainClassNameProperty = extension.mainClassName
      this.classFilesDirProperty = extension.classFilesDir
    }

    val checkTask = target.tasks.findNamed("check")
    checkTask?.configure {
      dependsOn(verifyMainClassExists)
    }
  }

  companion object {
    const val ExtensionName: String = "verifyMainClassExists"
    const val VerifyMainClassExistsTaskName: String = "verifyMainClassExists"
  }
}


/**
 * Resolves the main class name the verify task checks.
 *
 * Fails instead of guessing: a guessed class name (formerly `it.neckar.<project-name>`) verifies
 * nothing — it either fails later with a misleading "class file does not exist" for a class nobody
 * configured, or silently matches an unrelated class. The main class must come from
 * `application.mainClass` (or an explicit `verifyMainClassExists.mainClassName` override).
 */
internal fun resolveMainClassNameToVerify(projectPath: String, applicationMainClass: String?): String {
  return checkNotNull(applicationMainClass) {
    "Project $projectPath applies verifyMainClassExists but application.mainClass is not set — " +
      "configure application.mainClass (or set verifyMainClassExists.mainClassName explicitly)."
  }
}

interface VerifyMainClassExistsExtension {
  val mainClassName: Property<String>

  val classFilesDir: DirectoryProperty
}


abstract class VerifyMainClassExistsTask : DefaultTask() {
  private val mainMethodSearchStrings = setOf(
    "public static final void main(java.lang.String[])", //Kotlin
    "public static void main(java.lang.String[])", //Java
  )

  @get:Input
  abstract val mainClassNameProperty: Property<String>

  @get:InputDirectory
  abstract val classFilesDirProperty: DirectoryProperty

  @TaskAction
  fun verifyMainClassExists() {
    val mainClassName = mainClassNameProperty.get()

    val classFilesDir = classFilesDirProperty.get().asFile
    classFilesDir.requireDirExists { file ->
      "Class files dir does not exist @ ${file?.absolutePath}"
    }

    //Verifies that the class file exists
    val mainClassFile = File(classFilesDir, "${mainClassName.replace('.', '/')}.class")

    if (mainClassFile.exists().not()) {
      throw GradleException("Main class file does not exist @ ${mainClassFile.absolutePath}")
    }


    val process = ProcessBuilder("javap", "-public", mainClassFile.absolutePath)
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.bufferedReader().readText()

    val hasMainMethod = mainMethodSearchStrings.any {
      output.contains(it)
    }

    if (hasMainMethod.not()) {
      logger.debug("Output of javap:")
      logger.debug(output)

      throw GradleException("Main method does not exist in $mainClassName")
    }

    logger.info("Main method exists in [$mainClassName]")

    //Using reflection - not a good idea
    //val urlClassLoader = URLClassLoader.newInstance(arrayOf(outputDir.toURI().toURL()), null)
    //val mainClass = Class.forName(serviceMainClassName, false, urlClassLoader)
    //
    //if (mainClass.getDeclaredMethod("main", Array<String>::class.java) == null) {
    //  throw GradleException("Main method does not exist in $serviceMainClassName")
    //}
  }
}
