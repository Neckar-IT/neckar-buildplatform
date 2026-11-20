package it.neckar.gradle.shadow

import it.neckar.gradle.artifact.ArtifactKey
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime

/** Writes the [ArtifactKey] of the finished fat jar into the jar as [ArtifactKey.ResourcePath]; a cached jar keeps it. */
object FatJarArtifactKeyStamp {
  /** The content of [ArtifactKey.ResourcePath] for [artifactKey]. */
  private fun propertiesText(artifactKey: ArtifactKey): String {
    return "${ArtifactKey.PropertyName}=$artifactKey\n"
  }

  /** Adds or replaces [ArtifactKey.ResourcePath] in [fatJar]. */
  fun write(fatJar: File, artifactKey: ArtifactKey) {
    FileSystems.newFileSystem(URI.create("jar:${fatJar.toURI()}"), emptyMap<String, Any>()).use { zip ->
      val entry = zip.getPath(ArtifactKey.ResourcePath)
      Files.write(entry, propertiesText(artifactKey).toByteArray(Charsets.UTF_8))
      // All three times: the modification time alone leaves the time of this run in the entry's extra field, and a rerun differs by bytes.
      Files.getFileAttributeView(entry, BasicFileAttributeView::class.java).setTimes(FixedEntryTime, FixedEntryTime, FixedEntryTime)
    }
  }

  private val FixedEntryTime: FileTime = FileTime.fromMillis(0L)
}
