package it.neckar.gradle

import it.neckar.gradle.resources.publishResourcesOfWebTargets
import it.neckar.projects.ConfiguredProject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/**
 * What a build hands to the conventions of a module. The defaults serve a build outside the monorepo:
 * skip flags from Gradle properties or environment variables, the Detekt configuration of the build platform
 * and the neckar-rules rule set in the version of the property [NeckarOpenVersionProperty].
 * Detekt also loads `com.gitlab.cromefire:detekt-gitlab-report`, which only the GitLab Maven repository
 * `https://gitlab.com/api/v4/projects/25796063/packages/maven` serves; [NeckarBasePlugin] declares that repository, a build without it
 * declares the repository itself.
 */
class ModuleConventionSettings(
  /** Answers the skip flags of the tests and of the Linux target, e.g. `skipSlowTests`. */
  val skipPropertyLookup: SkipPropertyLookup = SkipPropertyLookup.GradlePropertyOrEnvironment,
  /** The Detekt configuration files of a module, in the order Detekt layers them; read when Detekt runs. */
  val detektConfigFiles: (Project) -> List<File> = { project -> listOf(project.buildplatformDetektConfig()) },
  /** The version of the rule set `it.neckar.open:detekt` a module analyses with. */
  val detektRuleSetVersion: (Project) -> String = { project -> project.neckarOpenVersion() },
)

/**
 * The conventions of a Kotlin/JVM module: Kotlin JVM, Detekt, Kover, JUnit, the sources and javadoc jars, a manifest with constant
 * attributes and the Java toolchain [JvmType.JavaLatestLTS].
 */
fun Project.configureJvmModule(settings: ModuleConventionSettings = ModuleConventionSettings()) {
  configureJvmCommon(settings)
  configureToolchainJava25LTS()
}

/**
 * The conventions of a KSP processor, a Kotlin/JVM module. A processor may depend on the projects whose path starts with
 * [processorPathPrefix], e.g. `:internal:open:ksp`, and on [allowedDependencyPaths] alone, e.g. `:internal:open:annotations`.
 */
fun Project.configureKspProcessorModule(processorPathPrefix: String, allowedDependencyPaths: Set<String>, settings: ModuleConventionSettings = ModuleConventionSettings()) {
  configureJvmModule(settings)

  val project = this
  afterEvaluate {
    val notAllowedProjectDependencies = findAllProjectDependencies(listOf("api", "runtimeClasspath", "compileOnly"))
      .filter { it != project }
      .filter { it.path.startsWith(processorPathPrefix).not() }
      .filter { it.path !in allowedDependencyPaths }

    if (notAllowedProjectDependencies.isNotEmpty()) {
      throw GradleException("Project dependencies not allowed for KSP processor project [$path]: but has ${notAllowedProjectDependencies.map { it.path }}")
    }
  }
}

/**
 * The conventions of a Kotlin multiplatform module. The module builds for the targets of [configuredProject];
 * an error message names [registration] as the place to change them.
 */
fun Project.configureMultiplatformModule(configuredProject: ConfiguredProject, registration: ProjectRegistration, settings: ModuleConventionSettings = ModuleConventionSettings()) {
  //Ensure there are no "forgotten" source directories - this might happen when a project is converted from a JVM or JS project
  requireDirectoryEmpty("src/main/kotlin")
  requireDirectoryEmpty("src/main/java")
  requireDirectoryEmpty("src/main/resources")
  requireDirectoryEmpty("src/test/kotlin")
  requireDirectoryEmpty("src/test/java")
  requireDirectoryEmpty("src/test/resources")

  verifyTargetSourceDirectories(configuredProject, registration)

  apply(plugin = BuildplatformPlugins.KotlinMultiPlatform)
  apply(plugin = BuildplatformPlugins.Detekt)

  configureKotlin()

  val kotlinMultiplatformExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
  kotlinMultiplatformExtension.applyMultiplatformKotlinConfiguration()
  kotlinMultiplatformExtension.declareTargets(this, configuredProject.targets, settings.skipPropertyLookup)

  verifyDeclaredTargets(configuredProject, registration)

  //A browser bundle resolves resources from a directory, not from a classpath, so a consumer has
  //to be able to pull this module's resources out of the dependency graph
  publishResourcesOfWebTargets()

  // A module without a JVM target has no Java toolchain to pin, no JUnit platform to run and
  // nothing for Kover to instrument.
  if (configuredProject.hasJvmTarget) {
    apply(plugin = BuildplatformPlugins.Kover)
    configureJunit(settings.skipPropertyLookup)
    configureToolchain(JvmType.JavaLatestLTS)
    configureCoverage()
  }

  configureDetekt(settings) {
    source.setFrom(files(multiplatformDetektSourceDirectories()))
  }

  registerPrintSourceSetsTask()
}

private fun Project.configureJvmCommon(settings: ModuleConventionSettings) {
  //Ensure there are no "invalid" source directories - this might happen when a project has been converted from a multiplatform project
  requireDirectoryEmpty("src/commonMain/kotlin")
  requireDirectoryEmpty("src/jsMain/kotlin")
  requireDirectoryEmpty("src/jvmMain/kotlin")
  requireDirectoryEmpty("src/jvmTest/resources")
  requireDirectoryEmpty("src/jsTest/resources")

  apply(plugin = BuildplatformPlugins.Java)
  apply(plugin = BuildplatformPlugins.JavaLibrary)
  apply(plugin = BuildplatformPlugins.KotlinJvm)
  apply(plugin = BuildplatformPlugins.Detekt)
  apply(plugin = BuildplatformPlugins.Kover)

  configureKotlin()

  configureJunit(settings.skipPropertyLookup)

  (extensions.findByName("sourceSets") as SourceSetContainer?)?.let {
    tasks.register<Jar>("sourcesJar") {
      group = "Build"
      description = "Assembles the sources jar."

      dependsOn("jar")
      from(it.getByName<SourceSet>("main").allSource)
      archiveClassifier = "sources"
    }
    tasks.register<Jar>("javadocJar") {
      group = "Build"
      description = "Assembles the javadoc jar."

      dependsOn("jar")
      archiveClassifier = "javadoc"
    }
  }

  tasks.named<Jar>("jar") {
    manifest {
      //Only constant attributes: library jar manifests must be a pure function of the sources.
      //Git info belongs into leaf fat jars as a resource.
      attributes(
        "Created-By" to "Neckar IT GmbH",
        "Project" to project.name,
      )
    }
  }

  // Alias: `gradle jvmSourcesJar` at the root builds the sources jar of every JVM module.
  tasks.register("jvmSourcesJar") {
    dependsOn("sourcesJar")
  }

  configureDetekt(settings) {
    source.setFrom(
      files(
        "src/main/kotlin",
        "src/main/java",
      )
    )
  }

  configureCoverage()
}

/** Fails when the directory [relativePath] of this project holds a file. */
fun Project.requireDirectoryEmpty(relativePath: String) {
  val dir = file(relativePath)

  require(dir.listFiles().isNullOrEmpty()) {
    "$relativePath must be empty in $path (${dir.absolutePath})"
  }
}
