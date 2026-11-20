package it.neckar.gradle.pnpm.dependency

import it.neckar.projects.GradleProjectPath
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import javax.inject.Inject

/**
 * Holds the one [PackageNameRegistry] of a build. Gradle creates the service on first use and
 * discards it with the build, so a daemon never carries a registry over from an earlier build.
 */
abstract class PackageNameRegistryService : BuildService<PackageNameRegistryService.Params> {
  interface Params : BuildServiceParameters {
    /** Gradle paths of the pnpm modules, each with its `package.json` below [rootDirectory]. */
    val pnpmModules: ListProperty<String>

    val rootDirectory: DirectoryProperty
  }

  @get:Inject
  abstract val providers: ProviderFactory

  val registry: PackageNameRegistry by lazy {
    PackageNameRegistry.create(
      providers = providers,
      rootDirectory = parameters.rootDirectory.get(),
      pnpmModules = parameters.pnpmModules.get().map { GradleProjectPath(it) },
    )
  }

  companion object {
    const val ServiceName: String = "packageNameRegistry"

    /**
     * Registers the service for the build on the first call; every later call returns that registration.
     * [pnpmModules] are the Gradle paths of every pnpm project of the project registry.
     */
    fun register(project: Project, pnpmModules: Provider<List<GradleProjectPath>>): Provider<PackageNameRegistryService> {
      return project.gradle.sharedServices.registerIfAbsent(ServiceName, PackageNameRegistryService::class.java) {
        // The parameters of a build service carry text; the path travels as its value.
        parameters.pnpmModules.set(pnpmModules.map { paths -> paths.map { it.path } })
        parameters.rootDirectory.set(project.rootProject.layout.projectDirectory)
      }
    }
  }
}
