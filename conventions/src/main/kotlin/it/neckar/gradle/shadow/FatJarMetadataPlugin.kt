package it.neckar.gradle.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import it.neckar.gradle.MainSourceSetName
import it.neckar.gradle.ProcessResourcesTaskName
import it.neckar.gradle.artifact.ArtifactKeyReport
import it.neckar.gradle.artifact.ContentAddressedInputs
import it.neckar.gradle.requireNotNull
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import java.io.File

/**
 * Gives every fat jar in this repository one shared, deliberate strategy for the metadata files
 * that third-party libraries bring along.
 *
 * Every library packs its own `META-INF/LICENSE`, `META-INF/NOTICE`, service-provider registry and
 * similar files. In a fat jar all of them collapse onto the same path, so without a strategy the
 * Shadow plugin keeps every copy, warns about the duplicates, and the JVM silently reads whichever
 * one happens to come first. The strategy applied here:
 *
 * - Legal and informational text files ([MergedMetadataResources]) are concatenated, so every
 *   library's attribution survives in the fat jar instead of one arbitrary library's winning.
 * - Service-provider registries below `META-INF/services` are merged entry by entry via
 *   `mergeServiceFiles()`, so every `ServiceLoader` implementation stays discoverable.
 * - Build-time-only metadata ([DroppedBuildTimeMetadata]) is dropped: it describes the single
 *   library jar it came from, has no meaning inside a fat jar, and is never read at runtime.
 *
 * The plugin is applied to every project from the root build so the strategy is identical for all
 * `shadowJar` tasks instead of being repeated (and drifting) per module.
 *
 * Every fat jar is then verified to contain each path exactly once, so a new duplicate fails the
 * build instead of silently reintroducing a "whichever copy comes first wins" situation, and to
 * hold every provider class named by a registry that is read at runtime, unless the JDK provides it
 * ([FatJarServiceProviders]), so a provider the minimizer dropped fails the build instead of the
 * first `ServiceLoader` or JavaMail `Session` lookup at runtime.
 *
 * After these checks [FatJarArtifactKeyStamp] writes the artifact key into the jar and its report below `build/artifact-key/<task name>`.
 *
 * A duplicate also arises without any library being at fault, and that one no rule about metadata can
 * catch: a file another task copied into the module's resources output directory is packed next to
 * the copy the dependency jar contributes. Gradle never deletes such a file — not when the writing
 * task is removed, not when the owning task runs again — so the plugin rejects a second writer
 * instead, and the resources output directory keeps a single owner.
 */
class FatJarMetadataPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.withType(ShadowPlugin::class.java) {
      target.tasks.withType(ShadowJar::class.java).configureEach {
        //Merges META-INF/services entries one by one - a plain concatenation would work here too,
        //but mergeServiceFiles() also strips duplicate implementation lines.
        mergeServiceFiles()

        MergedMetadataResources.forEach { resourcePath ->
          transform(AppendingTransformer::class.java) {
            resource.set(resourcePath)
            separator.set("\n")
          }
        }

        DroppedBuildTimeMetadata.forEach { pattern ->
          exclude(pattern)
        }

        //Shadow only warns about duplicates, and that warning drowns in the build log. doLast captures
        //providers, a path and objects only, never the plugin instance (configuration-cache safe).
        val fatJarProvider = archiveFile
        val cleanTaskPath = "${target.path}:clean"
        val reportDirectory = target.layout.buildDirectory.dir(ArtifactKeyReport.directoryPath(name))
        outputs.dir(reportDirectory)
        doLast {
          val fatJar = fatJarProvider.get().asFile
          FatJarDuplicateEntries.verify(fatJar, cleanTaskPath)
          FatJarServiceProviders.verify(fatJar)
          // After both checks: the stamp rewrites the jar through a zip file system, which keeps one entry per name.
          val inputs = ContentAddressedInputs.ofJar(fatJar)
          FatJarArtifactKeyStamp.write(fatJar, inputs.key)
          ArtifactKeyReport.write(reportDirectory.get().asFile, inputs)
        }
      }

      target.plugins.withType(JavaPlugin::class.java) {
        //A second writer can only be registered while the module's build script runs
        target.afterEvaluate {
          val mainSourceSet: SourceSet = target.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName(MainSourceSetName)

          failOnWritersBelow(target, mainSourceSet.output.resourcesDir.requireNotNull { "[${target.path}] has no resources output directory" })
        }
      }
    }
  }

  /**
   * Fails when a task other than `processResources` writes below [resourcesOutputDirectory].
   *
   * Gradle deletes none of what such a task leaves there — not when the writing task is removed, not
   * when `processResources` runs again. The files stay on the classpath and in every fat jar until
   * somebody runs `clean`, where they collide with the copy a dependency jar contributes. Letting the
   * task own a directory of its own and adding it via `processResources { from(<task>) }` keeps its
   * leftovers where nothing reads them.
   */
  private fun failOnWritersBelow(target: Project, resourcesOutputDirectory: File) {
    //Sync extends AbstractCopyTask, not Copy, so withType(Copy) does not see it
    val destinationsByTaskName: List<Pair<String, File?>> =
      target.tasks.withType(Copy::class.java).map { copyTask -> copyTask.name to copyTask.destinationDir } +
        target.tasks.withType(Sync::class.java).map { syncTask -> syncTask.name to syncTask.destinationDir }

    val writingTasks: List<String> = destinationsByTaskName
      .filter { (taskName, destination) ->
        taskName != ProcessResourcesTaskName && destination != null && destination.startsWith(resourcesOutputDirectory)
      }
      .map { (taskName, _) -> taskName }
      .sorted()

    if (writingTasks.isEmpty()) {
      return
    }

    throw InvalidUserDataException(
      "[${target.path}] builds a fat jar, so processResources must be the only task writing into $resourcesOutputDirectory. " +
        "${writingTasks.joinToString()} writes there as well. Give that task a directory of its own below `build/` and add it " +
        "to the resources instead: `processResources { from(<task>) { into(\"assets\") } }`. Writing into another task's output " +
        "directory leaves files behind that Gradle no longer knows about once the writing task is gone, and every one of them " +
        "ends up in the fat jar next to the copy a dependency jar contributes."
    )
  }

  companion object {
    /**
     * Metadata files that are concatenated into a single entry, keeping every library's content.
     *
     * These are all plain text: license and notice texts, the dependency list Maven's
     * `dependency:list` writes, and Netty's version manifest (a `.properties` file whose entries are
     * namespaced per Netty module, so concatenating loses nothing).
     *
     * Concatenated with the generic [AppendingTransformer], deliberately not with Shadow's
     * `MergeLicenseResourceTransformer` / `ApacheNoticeResourceTransformer`. Those exist for a
     * different goal — producing a distributable license/notice for a project that is itself
     * Apache-style: `MergeLicenseResourceTransformer` requires a mandatory `artifactLicense` file and
     * defaults its SPDX id to `Apache-2.0`, `ApacheNoticeResourceTransformer` injects the "NOTICE file
     * corresponding to section 4d of The Apache License … The Apache Software Foundation" preamble.
     * Both editorialize the content. Our goal is the opposite: preserve every bundled library's
     * attribution verbatim, so the raw concatenation is the correct tool here.
     */
    val MergedMetadataResources: List<String> = listOf(
      "META-INF/LICENSE",
      "META-INF/LICENSE.txt",
      "META-INF/LICENSE.md",
      "META-INF/NOTICE",
      "META-INF/NOTICE.txt",
      "META-INF/NOTICE.md",
      "META-INF/NOTICE.markdown",
      "META-INF/DEPENDENCIES",
      "META-INF/FastDoubleParser-LICENSE",
      "META-INF/io.netty.versions.properties",
      "license/LICENSE",
      "license/NOTICE",
      "license/LICENSE.dom-software.txt",
      "license/LICENSE.dom-documentation.txt",
      "license/README.dom.txt",
    )

    /**
     * Metadata that describes a single library jar and carries no meaning once that jar has been
     * folded into a fat jar.
     *
     * - `META-INF/jandex.idx` is Jandex's pre-built annotation index of exactly one jar. A fat jar's
     *   index would have to be rebuilt from the merged content; keeping one library's index would
     *   describe a jar that no longer exists.
     * - Everything under an `OSGI-INF` directory inside a multi-release jar's version folder is OSGi
     *   bundle metadata (see the pattern below). Fat jars are launched with a plain classpath, never
     *   as an OSGi bundle. A plain top-level `OSGI-INF` directory is intentionally not dropped here —
     *   none has shown up duplicated, and the verification would flag it if one ever did.
     */
    val DroppedBuildTimeMetadata: List<String> = listOf(
      "META-INF/jandex.idx",
      "META-INF/versions/*/OSGI-INF/**",
    )
  }
}
