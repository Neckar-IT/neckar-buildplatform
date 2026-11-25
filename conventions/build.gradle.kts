// The convention plugins without operational knowledge and the helpers the build scripts of a project call.
// Compiled with Gradle's embedded Kotlin like every Gradle plugin; the Kotlin Gradle plugin it applies is the catalog's.
plugins {
  `kotlin-dsl`
}

description = "The convention plugins of neckar IT without operational knowledge: Kotlin, KSP, detekt and the helpers of the build scripts."

/** The Kotlin the production code compiles with, from the catalog; the conventions apply its Gradle plugin. */
val applicationKotlinVersion: String =
  extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("kotlin").get().requiredVersion

dependencies {
  api(kotlin("gradle-plugin", applicationKotlinVersion))
  // The kotlinxSerialization accessor applies the serialization compiler plugin in the version of the Kotlin compiler.
  api(kotlin("serialization", applicationKotlinVersion))
  // The module conventions apply Detekt and Kover.
  api(libs.detekt.gradle.plugin)
  api(libs.kover.gradle.plugin)
  // DisableDistTasksPlugin reacts to the Shadow plugin.
  api(libs.shadow.gradle.plugin)
  // The KSP conventions apply the KSP Gradle plugin and pass the option names of ksp-contract to their processors.
  api(libs.symbol.processing.gradle.plugin)
  api(project(":ksp-contract"))
  // ConfiguredProject.openapiSpecTask names the task of a registered project.
  api(project(":projects"))
  // configureJavaFX applies the JavaFX plugin.
  api(libs.javafx.plugin)
  // The pnpm conventions apply the node plugin and limit its tasks; TailwindPlugin runs after its pnpm install.
  api(libs.node.gradle.plugin)
  // The download accessor applies the download plugin without a version; its classes come with the conventions.
  api(libs.gradle.download.task)
  // CapabilityConflictPlugin applies the conflict detection of this plugin.
  implementation(libs.jvm.dependency.conflict.resolution.gradle.plugin)
  // The pnpm conventions read the package.json of each pnpm module.
  implementation(libs.kotlinx.serialization.json)
  // GenerateIconsPlugin and the npm bundle tasks.
  implementation(libs.guava)
  // GzipNpmModuleTask writes the npm tarball.
  implementation(libs.commons.compress)

  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.assertk.jvm)
}

gradlePlugin {
  plugins {
    register("VerifyMainClassExistsPlugin") {
      id = "it.neckar.verify.main-class-exists"
      implementationClass = "it.neckar.gradle.verify.VerifyMainClassExistsPlugin"
    }
    register("DisableDistTasksPlugin") {
      id = "it.neckar.performance.disable-dist-tasks"
      implementationClass = "it.neckar.gradle.performance.DisableDistTasksPlugin"
    }
    register("KspBoxingPlugin") {
      id = "it.neckar.ksp.boxing"
      implementationClass = "it.neckar.gradle.ksp.boxing.KspBoxingPlugin"
    }
    register("KspSerializationPlugin") {
      id = "it.neckar.ksp.serialization"
      implementationClass = "it.neckar.gradle.ksp.serialization.KspSerializationPlugin"
    }
    register("TypescriptDefinitionGenerationPlugin") {
      id = "it.neckar.ksp.generating.ts"
      implementationClass = "it.neckar.gradle.ksp.generating.ts.TypescriptDefinitionGenerationPlugin"
    }
    register("TypesListGeneratorPlugin") {
      id = "it.neckar.generation.types-list"
      implementationClass = "it.neckar.gradle.ksp.generating.types.list.TypesListGeneratorPlugin"
    }
    register("TypesListCollectorPlugin") {
      id = "it.neckar.generation.types-list-collector"
      implementationClass = "it.neckar.gradle.ksp.generating.types.list.collection.TypesListCollectorPlugin"
    }
    register("SpecHarvestPlugin") {
      id = "it.neckar.ksp.spec-harvest"
      implementationClass = "it.neckar.gradle.ksp.spec.harvest.SpecHarvestPlugin"
    }
    register("OpenApiGenerationConfigPlugin") {
      id = "it.neckar.openapi.generation-config"
      implementationClass = "it.neckar.gradle.openapi.OpenApiGenerationConfigPlugin"
    }
    register("DependencyFencePlugin") {
      id = "it.neckar.dependency-fence"
      implementationClass = "it.neckar.gradle.fence.DependencyFencePlugin"
    }
    register("OpenModulePlugin") {
      id = "it.neckar.open-module"
      implementationClass = "it.neckar.gradle.fence.OpenModulePlugin"
    }
    register("TailwindPlugin") {
      id = "it.neckar.tailwind"
      implementationClass = "it.neckar.gradle.tailwind.TailwindPlugin"
    }
    register("GenerateIconsPlugin") {
      id = "it.neckar.generate-icons"
      implementationClass = "it.neckar.gradle.icons.GenerateIconsPlugin"
    }
    register("NpmBundlePlugin") {
      id = "it.neckar.npm-bundle"
      implementationClass = "it.neckar.gradle.npmbundle.NpmBundlePlugin"
    }
    register("WebResourcesFromDependenciesPlugin") {
      id = "it.neckar.web-resources-from-dependencies"
      implementationClass = "it.neckar.gradle.resources.WebResourcesFromDependenciesPlugin"
    }
    register("NoInlineScriptsPlugin") {
      id = "it.neckar.no-inline-scripts"
      implementationClass = "it.neckar.gradle.html.NoInlineScriptsPlugin"
    }
    register("ModuleGroupPlugin") {
      id = "it.neckar.module-group"
      implementationClass = "it.neckar.gradle.modulegroup.ModuleGroupPlugin"
    }
    register("CapabilityConflictPlugin") {
      id = "it.neckar.dependencies.capability-conflicts"
      implementationClass = "it.neckar.gradle.dependencies.CapabilityConflictPlugin"
    }
    register("NeckarBasePlugin") {
      id = "it.neckar.base"
      implementationClass = "it.neckar.gradle.NeckarBasePlugin"
    }
    register("MavenCentralPublishingPlugin") {
      id = "it.neckar.maven-central-publishing"
      implementationClass = "it.neckar.gradle.publishing.MavenCentralPublishingPlugin"
    }
    register("FatJarMetadataPlugin") {
      id = "it.neckar.shadow.fat-jar-metadata"
      implementationClass = "it.neckar.gradle.shadow.FatJarMetadataPlugin"
    }
  }
}

// configureDetekt reads the Detekt configuration of the build platform from the classpath of the conventions.
tasks.named<ProcessResources>("processResources") {
  from(layout.projectDirectory.file("../config/detekt/detekt.yml")) {
    into("it/neckar/gradle/detekt")
  }
}

// The stricter validation of a published plugin rejects the task types moved from build-logic, which turns it off as well.
tasks.withType<ValidatePlugins>().configureEach {
  enableStricterValidation = false
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform {
    includeEngines("junit-jupiter")
  }

  filter {
    includeTestsMatching("*Test")
    isFailOnNoMatchingTests = true
  }
}
