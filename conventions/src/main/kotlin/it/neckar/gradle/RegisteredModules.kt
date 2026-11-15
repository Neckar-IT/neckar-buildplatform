package it.neckar.gradle

import it.neckar.projects.ConfiguredProject
import it.neckar.projects.ProjectType
import org.gradle.api.Project

/**
 * Applies the conventions of its type to every Kotlin module of [projects], before their build scripts are evaluated, with the
 * [ModuleConventionSettings] defaults of a build outside the monorepo. [registrationOf] names the registry entry of a module in an
 * error message.
 */
fun configureRegisteredKotlinModules(
  baseProject: Project,
  projects: List<ConfiguredProject>,
  kspProcessorPathPrefix: String,
  allowedKspDependencyPaths: Set<String>,
  registrationOf: (ConfiguredProject) -> ProjectRegistration,
) {
  projects.forEach { configuredProject ->
    val project: Project = configuredProject.getProject(baseProject)
    when (configuredProject.type) {
      ProjectType.KotlinMultiplatform -> project.configureMultiplatformModule(configuredProject, registrationOf(configuredProject))
      ProjectType.KotlinJvm -> project.configureJvmModule()
      ProjectType.KspProcessor -> project.configureKspProcessorModule(kspProcessorPathPrefix, allowedKspDependencyPaths)
      else -> throw IllegalArgumentException("${configuredProject.path} is of type ${configuredProject.type}, which has no Kotlin module conventions")
    }
  }
}
