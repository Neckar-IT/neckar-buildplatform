package it.neckar.gradle.ksp

import com.google.devtools.ksp.gradle.KspExtension
import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.isMultiplatform
import it.neckar.gradle.ksp.options.KspPluginOptions
import it.neckar.gradle.neckarOpenNotation
import it.neckar.gradle.requireNotNull
import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.internal.cc.base.logger
import org.gradle.kotlin.dsl.findByType

/**
 * Abstract base class for KSP plugins
 */
abstract class BaseKspPlugin : Plugin<Project> {
  /**
   * The KSP processor this plugin adds, e.g. `it.neckar.open.ksp:serialization-verifier`; the root build
   * resolves it to the processor project.
   */
  abstract val kspProcessor: MavenCoordinate

  override final fun apply(target: Project) {
    target.plugins.apply(BuildplatformPlugins.Ksp)

    // workaround: dependency=libs:com-google-devtools-ksp:2.3.12 observed-on=2026-10-07 upstream=https://github.com/google/ksp/issues/2956
    // Since KSP 2.3.10 the 'ksp' configuration only applies to the main compilation; the test compilation needs its own 'kspTest' entry.
    //Multiplatform projects register the processor per target for the main compilations only.
    val allowedConfigurationNames = when {
      target.isMultiplatform() -> setOf("kspJs", "kspJvm", "kspNative")
      else -> setOf("ksp", "kspTest")
    }

    //Detect the supported configurations
    val kspConfigurations = target.configurations
      .filter { allowedConfigurationNames.contains(it.name) }


    //Check if there is at least one configuration
    if (kspConfigurations.isEmpty()) {
      val possibleCandidates = target.configurations
        .map { it.name }
        .filter {
          it.lowercase().contains("ksp")
        }

      throw IllegalStateException("No [ksp] configuration found for ${target.name}. Possible candidates: $possibleCandidates")
    }

    logger.debug("#######################################################################")
    logger.debug("Applying KSP plugin [${this::class.qualifiedName}] to project [${target.path}]")
    logger.debug("Using KSP configurations: ${kspConfigurations.map { it.name }}")
    logger.debug("#######################################################################")

    kspConfigurations.forEach {
      target.dependencies.add(it.name, target.neckarOpenNotation(kspProcessor))
    }

    val kspExtension = target.extensions.findByType<KspExtension>().requireNotNull { "KSP extension not found" }

    //Add the default options (for all projects)
    //The configuration cache serializes each argument provider into every KSP task: the lambda captures values, never the Project
    val commonArgs: List<String> = KspPluginOptions.Common.create(target)
    kspExtension.arg {
      commonArgs
    }

    applyCustom(target, kspExtension, kspConfigurations)
  }

  /**
   * Custom apply - can be used to apply additional configurations
   */
  open fun applyCustom(target: Project, kspExtension: KspExtension, configurations: List<Configuration>) {
  }
}
