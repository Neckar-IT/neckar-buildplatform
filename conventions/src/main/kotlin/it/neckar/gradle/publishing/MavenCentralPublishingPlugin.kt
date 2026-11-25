package it.neckar.gradle.publishing

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.plugins.signing.SigningExtension

/**
 * Applied to the parent of the projects published to Maven Central: `verifyMavenCentralPublishing` publishes each of them signed
 * into the build directory and checks the result against [MavenCentralPublicationRules]. Uses the Gradle API alone, for `buildSrc`.
 */
class MavenCentralPublishingPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val repositoryDirectory: Provider<Directory> = target.layout.buildDirectory.dir(RepositoryPath)

    // A file of an earlier run would pass as an artifact of this one.
    val deleteRepository: TaskProvider<Delete> = target.tasks.register(DeleteRepositoryTaskName, Delete::class.java) {
      delete(repositoryDirectory)
    }
    val verify: TaskProvider<VerifyMavenCentralPublishingTask> = target.tasks.register(VerifyTaskName, VerifyMavenCentralPublishingTask::class.java) {
      group = "verification"
      description = "Publishes the Maven Central publications below this project to build/$RepositoryPath and checks every POM against the rules of Maven Central"
      repository.set(repositoryDirectory)
      // Also without any publish task: the task then reports a repository without a POM.
      dependsOn(deleteRepository)
    }

    target.subprojects {
      val subproject: Project = this
      pluginManager.withPlugin(VanniktechMavenPublishPluginId) {
        subproject.extensions.configure(PublishingExtension::class.java) {
          repositories.maven {
            name = RepositoryName
            url = repositoryDirectory.get().asFile.toURI()
          }
        }
        subproject.tasks.withType(PublishToMavenRepository::class.java).configureEach {
          if (name.endsWith(RepositoryTaskSuffix)) {
            dependsOn(deleteRepository)
          }
        }
        verify.configure { dependsOn("${subproject.path}:publishAllPublications$RepositoryTaskSuffix") }
        subproject.signEveryPublication()
      }
    }
  }

  /**
   * Every task that publishes a publication signs it, a `-SNAPSHOT` and `publishToMavenLocal` included; `build` signs nothing.
   * CI signs with the Gradle property `signingInMemoryKey`; a workstation without it signs through the local GPG agent.
   */
  private fun Project.signEveryPublication() {
    afterEvaluate {
      val signing: SigningExtension = extensions.findByType(SigningExtension::class.java)
        ?: throw GradleException("$path applies $VanniktechMavenPublishPluginId without mavenPublishing { signAllPublications() }: its publications have no signature")
      signing.apply {
        if (providers.gradleProperty("signingInMemoryKey").isPresent.not()) {
          useGpgCmd()
        }
        isRequired = true
      }
    }
  }

  companion object {
    const val VerifyTaskName: String = "verifyMavenCentralPublishing"
    const val DeleteRepositoryTaskName: String = "deleteMavenCentralVerificationRepository"

    /** The name of the Maven repository; `maven-publish` derives `publishAllPublicationsToMavenCentralVerificationRepository` from it. */
    const val RepositoryName: String = "MavenCentralVerification"

    /** The repository directory, relative to the build directory of the project the plugin is applied to. */
    const val RepositoryPath: String = "maven-central-verification/repository"

    /** The plugin both builds apply to exactly the projects they publish to Maven Central. */
    private const val VanniktechMavenPublishPluginId: String = "com.vanniktech.maven.publish"

    /** The end of the name of every task `maven-publish` registers for the repository. */
    private const val RepositoryTaskSuffix: String = "To${RepositoryName}Repository"
  }
}
