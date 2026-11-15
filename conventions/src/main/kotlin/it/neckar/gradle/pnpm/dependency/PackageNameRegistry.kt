package it.neckar.gradle.pnpm.dependency

import it.neckar.projects.GradleProjectPath
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ProviderFactory

/**
 * Registry that maps npm package names to their corresponding Gradle project paths. The constructor is public for the tests of
 * the Ladle tasks in build-logic; a build creates the registry through [PackageNameRegistry.create].
 */
class PackageNameRegistry(
  private val packageNameToGradlePath: Map<NpmPackageName, GradleProjectPath>,
) {
  /**
   * The Gradle project path for [packageName].
   *
   * An unknown package is a defect, not an absence: dropping it removes the build-order edge and the
   * dependency's `dist/` input, leaving the consuming module up-to-date after its dependency changed.
   */
  fun findGradlePath(packageName: NpmPackageName): GradleProjectPath {
    return findGradlePathOrNull(packageName)
      ?: throw GradleException(
        "No pnpm module provides npm package '$packageName'. Either it is not registered as a Gradle " +
          "project (settings.gradle.kts and the project registry), or its package.json declares a different 'name'. " +
          "Registered packages: $size."
      )
  }

  fun findGradlePathOrNull(packageName: NpmPackageName): GradleProjectPath? = packageNameToGradlePath[packageName]

  /**
   * Returns the number of packages in the registry.
   */
  val size: Int = packageNameToGradlePath.size

  companion object {
    private val logger = Logging.getLogger(PackageNameRegistry::class.java)

    /**
     * Reads the `name` from the `package.json` of every module in [pnpmModules] through [providers], so
     * the files are build-configuration inputs. [rootDirectory] is the directory the module paths start from.
     */
    fun create(
      providers: ProviderFactory,
      rootDirectory: Directory,
      pnpmModules: List<GradleProjectPath>,
    ): PackageNameRegistry {
      val parser = PackageJsonParser()
      val mapping = mutableMapOf<NpmPackageName, GradleProjectPath>()

      pnpmModules.forEach { modulePath ->
        val packageJson = rootDirectory.file("${modulePath.filePath}/package.json")
        val packageJsonFile = packageJson.asFile
        val packageJsonText = providers.fileContents(packageJson).asText.orNull
          ?: throw GradleException("pnpm project '$modulePath' has no package.json at ${packageJsonFile.absolutePath}.")

        val packageName = parser.parse(packageJsonText, packageJsonFile).name
          ?: throw GradleException(
            "pnpm project '$modulePath' declares no 'name' in ${packageJsonFile.absolutePath}."
          )

        // put returns the previous holder — a second module claiming one name would otherwise
        // silently take over the mapping and misdirect every dependency on it.
        mapping.put(packageName, modulePath)?.let { alreadyClaimedBy ->
          throw GradleException(
            "pnpm projects '$modulePath' and '$alreadyClaimedBy' both declare the npm package name '$packageName'."
          )
        }

        logger.debug("Mapped package '$packageName' -> '$modulePath'")
      }

      logger.info("Built package name registry with ${mapping.size} entries")
      return PackageNameRegistry(mapping)
    }
  }
}
