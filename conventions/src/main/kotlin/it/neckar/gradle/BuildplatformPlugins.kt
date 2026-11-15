package it.neckar.gradle

/**
 * The IDs of the plugins the conventions of the build platform register, and of the third-party plugins they apply.
 */
object BuildplatformPlugins {
  const val Java: String = "java"

  const val JavaLibrary: String = "java-library"

  const val Detekt: String = "dev.detekt"

  const val Kover: String = "org.jetbrains.kotlinx.kover"

  const val KotlinMultiPlatform: String = "org.jetbrains.kotlin.multiplatform"

  const val KotlinJvm: String = "org.jetbrains.kotlin.jvm"

  const val KotlinxSerialization: String = "org.jetbrains.kotlin.plugin.serialization"

  const val VerifyMainClassExists: String = "it.neckar.verify.main-class-exists"

  const val DisableDistTasks: String = "it.neckar.performance.disable-dist-tasks"

  const val Ksp: String = "com.google.devtools.ksp"

  const val KspBoxing: String = "it.neckar.ksp.boxing"

  /** Verifies the serialization of the project's classes (`it.neckar.ksp.serialization.SerializationVerifierProcessor`). */
  const val KspSerialization: String = "it.neckar.ksp.serialization"

  const val SpecHarvest: String = "it.neckar.ksp.spec-harvest"

  const val GenerateTsDeclaration: String = "it.neckar.ksp.generating.ts"

  const val GenerateTypesList: String = "it.neckar.generation.types-list"

  /**
   * Collects the types-list from the dependencies or the subprojects.
   * Generates no types-list for the project itself; [GenerateTypesList] does.
   */
  const val TypesListCollector: String = "it.neckar.generation.types-list-collector"

  const val OpenapiGenerationConfig: String = "it.neckar.openapi.generation-config"

  /** Forbids project and external dependencies per configuration; see [it.neckar.gradle.fence.DependencyFencePlugin]. */
  const val DependencyFence: String = "it.neckar.dependency-fence"

  /** Applies a dependency fence that bans every project outside the open tree; see [it.neckar.gradle.fence.OpenModulePlugin]. */
  const val OpenModule: String = "it.neckar.open-module"

  const val Base: String = "org.gradle.base"

  const val Node: String = "com.github.node-gradle.node"

  const val GenerateIcons: String = "it.neckar.generate-icons"

  /**
   * Judges the HTML a project deploys: no `<script>` carrying code, because a document answering
   * under `script-src 'self'` has it parsed into the DOM and never run. Bundled via `frontendProject`.
   */
  const val NoInlineScripts: String = "it.neckar.no-inline-scripts"

  const val WebResourcesFromDependencies: String = "it.neckar.web-resources-from-dependencies"

  const val Tailwind: String = "it.neckar.tailwind"

  const val NpmBundle: String = "it.neckar.npm-bundle"

  /**
   * Applies the repository-wide strategy for third-party metadata files in fat jars
   * (license/notice texts, service-provider registries, build-time-only metadata). Reacts to the
   * Shadow plugin, so it can be applied to every project unconditionally.
   */
  const val FatJarMetadata: String = "it.neckar.shadow.fat-jar-metadata"

  /** See [it.neckar.gradle.dependencies.CapabilityConflictPlugin]. */
  const val CapabilityConflicts: String = "it.neckar.dependencies.capability-conflicts"

  /** See [it.neckar.gradle.NeckarBasePlugin]. */
  const val NeckarBase: String = "it.neckar.base"

  const val Download: String = "de.undercouch.download"

  /**
   * A directory whose direct modules carry the prefix of its name: `verifyModuleGroup` at `check`.
   */
  const val ModuleGroup: String = "it.neckar.module-group"

  /** The JavaFX plugin, for JavaFX 17 and newer. */
  const val Javafx: String = "org.openjfx.javafxplugin"
}
