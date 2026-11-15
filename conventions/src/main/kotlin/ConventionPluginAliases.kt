// The plugins { } accessors of the conventions, e.g. `plugins { kspSerialization }`; in the default package, so a build script needs no import.
import it.neckar.gradle.BuildplatformPlugins
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

inline val PluginDependenciesSpec.kotlinJvm: PluginDependencySpec
  get() = id(BuildplatformPlugins.KotlinJvm)

inline val PluginDependenciesSpec.kotlinxSerialization: PluginDependencySpec
  get() = id(BuildplatformPlugins.KotlinxSerialization)

inline val PluginDependenciesSpec.java: PluginDependencySpec
  get() = id(BuildplatformPlugins.Java)

inline val PluginDependenciesSpec.javaLibrary: PluginDependencySpec
  get() = id(BuildplatformPlugins.JavaLibrary)

inline val PluginDependenciesSpec.detekt: PluginDependencySpec
  get() = id(BuildplatformPlugins.Detekt)

inline val PluginDependenciesSpec.kover: PluginDependencySpec
  get() = id(BuildplatformPlugins.Kover)

inline val PluginDependenciesSpec.kotlinMultiPlatform: PluginDependencySpec
  get() = id(BuildplatformPlugins.KotlinMultiPlatform)

inline val PluginDependenciesSpec.generateIcons: PluginDependencySpec
  get() = id(BuildplatformPlugins.GenerateIcons)

inline val PluginDependenciesSpec.generateTsDeclaration: PluginDependencySpec
  get() = id(BuildplatformPlugins.GenerateTsDeclaration)

inline val PluginDependenciesSpec.npmBundle: PluginDependencySpec
  get() = id(BuildplatformPlugins.NpmBundle)

inline val PluginDependenciesSpec.verifyMainClassExists: PluginDependencySpec
  get() = id(BuildplatformPlugins.VerifyMainClassExists)

inline val PluginDependenciesSpec.webResourcesFromDependencies: PluginDependencySpec
  get() = id(BuildplatformPlugins.WebResourcesFromDependencies)

inline val PluginDependenciesSpec.ksp: PluginDependencySpec
  get() = id(BuildplatformPlugins.Ksp)

inline val PluginDependenciesSpec.kspBoxing: PluginDependencySpec
  get() = id(BuildplatformPlugins.KspBoxing)

inline val PluginDependenciesSpec.specHarvest: PluginDependencySpec
  get() = id(BuildplatformPlugins.SpecHarvest)

inline val PluginDependenciesSpec.kspSerialization: PluginDependencySpec
  get() = id(BuildplatformPlugins.KspSerialization)

inline val PluginDependenciesSpec.openapiGenerationConfig: PluginDependencySpec
  get() = id(BuildplatformPlugins.OpenapiGenerationConfig)

inline val PluginDependenciesSpec.disableDistTasks: PluginDependencySpec
  get() = id(BuildplatformPlugins.DisableDistTasks)

inline val PluginDependenciesSpec.generateTypesList: PluginDependencySpec
  get() = id(BuildplatformPlugins.GenerateTypesList)

inline val PluginDependenciesSpec.typesListCollector: PluginDependencySpec
  get() = id(BuildplatformPlugins.TypesListCollector)

inline val PluginDependenciesSpec.tailwind: PluginDependencySpec
  get() = id(BuildplatformPlugins.Tailwind)

inline val PluginDependenciesSpec.dependencyFence: PluginDependencySpec
  get() = id(BuildplatformPlugins.DependencyFence)

inline val PluginDependenciesSpec.openModule: PluginDependencySpec
  get() = id(BuildplatformPlugins.OpenModule)

inline val PluginDependenciesSpec.javafx: PluginDependencySpec
  get() = id(BuildplatformPlugins.Javafx)

inline val PluginDependenciesSpec.noInlineScripts: PluginDependencySpec
  get() = id(BuildplatformPlugins.NoInlineScripts)

inline val PluginDependenciesSpec.fatJarMetadata: PluginDependencySpec
  get() = id(BuildplatformPlugins.FatJarMetadata)

inline val PluginDependenciesSpec.download: PluginDependencySpec
  get() = id(BuildplatformPlugins.Download)

inline val PluginDependenciesSpec.moduleGroup: PluginDependencySpec
  get() = id(BuildplatformPlugins.ModuleGroup)
