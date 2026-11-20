package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.rootCause
import assertk.assertions.startsWith
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.kotlin.dsl.extra
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A build outside the monorepo applies the module conventions with the default [ModuleConventionSettings]: the Detekt configuration
 * comes from the classpath of the conventions, the rule set in the version of the property [NeckarOpenVersionProperty].
 * A convention dropped from [configureJvmModule] fails no build, so these tests pin the plugins and artifacts of a module.
 */
class ModuleConventionsTest {

  @TempDir
  lateinit var rootDir: File

  private val root: Project by lazy {
    ProjectBuilder.builder().withProjectDir(rootDir).build().also { it.extra[NeckarOpenVersionProperty] = "10.1.0" }
  }

  private fun module(name: String = "module"): Project {
    return ProjectBuilder.builder().withName(name).withParent(root).withProjectDir(rootDir.resolve(name)).build()
  }

  /** `coverage=true` goes into the root `gradle.properties`, which `providers.gradleProperty` reads like `-P`. */
  private fun moduleWithCoverage(): Project {
    File(rootDir, "gradle.properties").writeText("coverage=true\n")
    return module()
  }

  @Test
  fun `a JVM module gets the Java library, Kotlin, Detekt and Kover plugins`() {
    val project = module()

    project.configureJvmModule()

    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.Java)).isTrue()
    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.JavaLibrary)).isTrue()
    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.KotlinJvm)).isTrue()
    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.Detekt)).isTrue()
    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.Kover)).isTrue()
  }

  @Test
  fun `the rule set comes in the version of neckarOpenVersion`() {
    val project = module()

    project.configureJvmModule()

    val detektPlugins = project.configurations.getByName("detektPlugins").dependencies.map { "${it.group}:${it.name}:${it.version}" }
    assertThat(detektPlugins).contains("it.neckar.open:detekt:10.1.0")
  }

  @Test
  fun `Detekt reads the configuration of the build platform`() {
    val project = module()

    project.configureJvmModule()

    val configFiles = project.extensions.getByType(DetektExtension::class.java).config.files.toList()
    val bundledConfig = requireNotNull(javaClass.classLoader.getResource("it/neckar/gradle/detekt/detekt.yml")) { "the conventions lack detekt.yml" }.readText()
    assertThat(configFiles.map { it.readText() }).containsExactly(bundledConfig)
  }

  @Test
  fun `a KSP processor depending on a project outside the processor path fails`() {
    val outside = module("outside")
    val processor = module("processor")
    processor.configureKspProcessorModule(processorPathPrefix = ":ksp", allowedDependencyPaths = emptySet())
    processor.dependencies.add("api", outside)

    assertFailure { (processor as ProjectInternal).evaluate() }
      .rootCause()
      .messageContains("Project dependencies not allowed for KSP processor project [:processor]: but has [:outside]")
  }

  /** The Kover agent doubles the cold start of every test JVM; only a coverage run pays for it. */
  @Test
  fun `without -Pcoverage the test JVM runs without the Kover agent`() {
    val project = module()

    project.configureJvmModule()
    (project as ProjectInternal).evaluate()

    assertThat(project.koverAgentArguments()).isEmpty()
  }

  @Test
  fun `with -Pcoverage=true the test JVM runs with the Kover agent and a Kover report starts`() {
    val project = moduleWithCoverage()

    project.configureJvmModule()
    (project as ProjectInternal).evaluate()

    assertThat(project.koverAgentArguments().single()).startsWith("-javaagent:")
    val report = project.tasks.getByName("koverHtmlReport")
    report.actions.first().execute(report)
  }

  /** Without the agent a Kover report would show zero coverage; the report fails instead. */
  @Test
  fun `without -Pcoverage a Kover report fails`() {
    val project = module()

    project.configureJvmModule()
    (project as ProjectInternal).evaluate()

    val report = project.tasks.getByName("koverHtmlReport")
    assertFailure { report.actions.first().execute(report) }
      .messageContains("-Pcoverage=true")
  }

  private fun Project.koverAgentArguments(): List<String> =
    (tasks.getByName("test") as TestTask).jvmArgumentProviders
      .filter { it is Named && it.name == "koverArgumentsProvider" }
      .flatMap { it.asArguments() }

  /** The rule set module itself would depend on its own coordinate. */
  @Test
  fun `the Detekt rule-set module gets no rule-set dependency`() {
    val detektModule = module("detekt").also { it.group = "it.neckar.open" }

    detektModule.configureJvmModule()

    assertThat(detektModule.configurations.getByName("detektPlugins").dependencies.map { "${it.group}:${it.name}" }).doesNotContain("it.neckar.open:detekt")
  }

  @Test
  fun `the module publishes a sources jar and a javadoc jar`() {
    val project = module()

    project.configureJvmModule()

    val sourcesJar = project.tasks.named("sourcesJar", Jar::class.java).get()
    val javadocJar = project.tasks.named("javadocJar", Jar::class.java).get()

    assertThat(sourcesJar.archiveClassifier.get()).isEqualTo("sources")
    assertThat(javadocJar.archiveClassifier.get()).isEqualTo("javadoc")
    assertThat(project.tasks.findByName("jvmSourcesJar")).isNotNull()
  }

  /**
   * Library jar manifests must be a pure function of the sources — git information belongs into leaf
   * fat jars only, otherwise every commit invalidates every jar in the build cache.
   */
  @Test
  fun `the jar manifest carries constant attributes only`() {
    val project = module()

    project.configureJvmModule()

    val attributes = project.tasks.named("jar", Jar::class.java).get().manifest.attributes

    assertThat(attributes["Created-By"]).isEqualTo("Neckar IT GmbH")
    assertThat(attributes["Project"]).isEqualTo(project.name)
    // Manifest-Version is Gradle's own default entry; everything beyond it is ours and must stay constant.
    assertThat(attributes.keys.sorted()).isEqualTo(listOf("Created-By", "Manifest-Version", "Project"))
  }

  /**
   * A module converted from multiplatform to plain JVM keeps compiling from `src/main/kotlin` — a
   * leftover `src/commonMain/kotlin` would silently stop being compiled instead of failing.
   */
  @Test
  fun `a leftover multiplatform source directory is rejected`() {
    val project = module()
    rootDir.resolve("module/src/commonMain/kotlin/it/neckar").mkdirs()
    rootDir.resolve("module/src/commonMain/kotlin/it/neckar/Leftover.kt").writeText("package it.neckar")

    assertFailure { project.configureJvmModule() }
      .rootCause()
      .messageContains("src/commonMain/kotlin must be empty")
  }

  @Test
  fun `an empty leftover directory is tolerated`() {
    val project = module()
    rootDir.resolve("module/src/jsMain/kotlin").mkdirs()

    project.configureJvmModule()

    assertThat(project.plugins.hasPlugin(BuildplatformPlugins.KotlinJvm)).isTrue()
  }
}
