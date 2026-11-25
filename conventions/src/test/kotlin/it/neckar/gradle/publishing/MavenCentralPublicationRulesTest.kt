package it.neckar.gradle.publishing

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MavenCentralPublicationRulesTest {
  @TempDir
  lateinit var repository: File

  @Test
  fun `a signed release POM with every field and its sources and javadoc jars meets the rules`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1", VersionedDependency))

    val report: MavenCentralPublicationReport = MavenCentralPublicationRules.check(repository)

    assertThat(report.violations).isEmpty()
    assertThat(report.poms).containsExactly("it/neckar/open/unit/11.0.1/unit-11.0.1.pom")
  }

  @Test
  fun `a snapshot POM with timestamped file names meets the rules`() {
    publish("unit", "11.1.0-SNAPSHOT", pom("unit", "11.1.0-SNAPSHOT", VersionedDependency), stem = "unit-11.1.0-20261123.130323-1")
    versionDirectory("unit", "11.1.0-SNAPSHOT").resolve("maven-metadata.xml").writeText("<metadata/>")
    listOf(".md5", ".sha1", ".sha256", ".sha512").forEach {
      versionDirectory("unit", "11.1.0-SNAPSHOT").resolve("unit-11.1.0-20261123.130323-1.jar$it").writeText("checksum")
    }

    assertThat(MavenCentralPublicationRules.check(repository).violations).isEmpty()
  }

  @Test
  fun `a snapshot jar without its sources and javadoc jars is named by its timestamped file name`() {
    publish("unit", "11.1.0-SNAPSHOT", pom("unit", "11.1.0-SNAPSHOT"), stem = "unit-11.1.0-20261123.130323-1", artifacts = listOf(".jar"))

    assertThat(MavenCentralPublicationRules.check(repository).violations).containsExactly(
      "it/neckar/open/unit/11.1.0-SNAPSHOT/unit-11.1.0-20261123.130323-1.pom has no unit-11.1.0-20261123.130323-1-sources.jar beside it",
      "it/neckar/open/unit/11.1.0-SNAPSHOT/unit-11.1.0-20261123.130323-1.pom has no unit-11.1.0-20261123.130323-1-javadoc.jar beside it",
    )
  }

  @Test
  fun `a version catalog without a jar needs no sources and javadoc jars`() {
    publish("version-catalog", "11.0.1", pom("version-catalog", "11.0.1"), artifacts = listOf(".toml"))

    assertThat(MavenCentralPublicationRules.check(repository).violations).isEmpty()
  }

  @Test
  fun `a directory without a POM is a violation, a missing directory too`() {
    val missing: File = repository.resolve("missing")

    assertThat(MavenCentralPublicationRules.check(repository).violations).containsExactly("no POM below $repository")
    assertThat(MavenCentralPublicationRules.check(missing).violations).containsExactly("no POM below $missing")
  }

  @Test
  fun `a POM that is no XML is named`() {
    publish("unit", "11.0.1", "<project>")

    assertThat(violationsOf("unit", "11.0.1").single().substringBefore(':')).isEqualTo("is no well-formed XML")
  }

  @Test
  fun `a POM without a version is named`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1").replace("<version>11.0.1</version>", ""))

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no version")
  }

  @Test
  fun `a missing or blank name, description and url is named`() {
    val bare: String = pom("unit", "11.0.1")
      .replace("<name>unit</name>", "<name> </name>")
      .replace("<description>A library</description>", "")
      .replace("<url>$MirrorUrl</url>\n  <licenses>", "<licenses>")
    publish("unit", "11.0.1", bare)

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no name", "has no description", "has no url")
  }

  @Test
  fun `a license without a name is named`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1").replace("<name>Apache-2.0</name>", ""))

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no license with a name")
  }

  @Test
  fun `a developer without a name is named`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1").replace("<name>Johannes Schneider</name>", ""))

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no developer with a name")
  }

  @Test
  fun `a missing scm url and connection is named`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1").replace(Regex("<scm>.*</scm>", RegexOption.DOT_MATCHES_ALL), "<scm/>"))

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no scm url", "has no scm connection")
  }

  @Test
  fun `a dependency without a version is named with its coordinates`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1", VersionlessDependency))

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has the dependency it.neckar.open.meistercharts:meistercharts-core without a version")
  }

  @Test
  fun `a managed dependency without a version is named`() {
    publish("bom", "11.0.1", pom("bom", "11.0.1", "<dependencyManagement>$VersionlessDependency</dependencyManagement>"))

    assertThat(violationsOf("bom", "11.0.1")).containsExactly("has the dependency it.neckar.open.meistercharts:meistercharts-core without a version")
  }

  @Test
  fun `a file without its signature is named`() {
    publish("unit", "11.0.1", pom("unit", "11.0.1"))
    versionDirectory("unit", "11.0.1").resolve("unit-11.0.1.module").writeText("module")

    assertThat(violationsOf("unit", "11.0.1")).containsExactly("has no signature unit-11.0.1.module.asc")
  }

  /** The violations of the one POM of [artifact], without its path. */
  private fun violationsOf(artifact: String, version: String): List<String> {
    val prefix = "it/neckar/open/$artifact/$version/$artifact-$version.pom "
    return MavenCentralPublicationRules.check(repository).violations.map { it.removePrefix(prefix) }
  }

  private fun versionDirectory(artifact: String, version: String): File = repository.resolve("it/neckar/open/$artifact/$version")

  /** Writes [pom] and the files `<stem><suffix>` of [artifacts], each with its signature file. */
  private fun publish(
    artifact: String,
    version: String,
    pom: String,
    stem: String = "$artifact-$version",
    artifacts: List<String> = listOf(".jar", "-sources.jar", "-javadoc.jar"),
  ) {
    val directory: File = versionDirectory(artifact, version).apply { mkdirs() }
    directory.resolve("$stem.pom").writeText(pom)
    (artifacts + ".pom").forEach { suffix ->
      directory.resolve("$stem$suffix").takeUnless { it.exists() }?.writeText(suffix)
      directory.resolve("$stem$suffix.asc").writeText("signature")
    }
  }

  /** A POM of `it.neckar.open:<artifact>:<version>` with every field Maven Central asks for. */
  private fun pom(artifact: String, version: String, dependencies: String = ""): String = """
    |<?xml version="1.0" encoding="UTF-8"?>
    |<project xmlns="http://maven.apache.org/POM/4.0.0">
    |  <modelVersion>4.0.0</modelVersion>
    |  <groupId>it.neckar.open</groupId>
    |  <artifactId>$artifact</artifactId>
    |  <version>$version</version>
    |  <name>$artifact</name>
    |  <description>A library</description>
    |  <url>$MirrorUrl</url>
    |  <licenses><license><name>Apache-2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0</url></license></licenses>
    |  <developers><developer><id>jschneider</id><name>Johannes Schneider</name></developer></developers>
    |  <scm>
    |    <connection>scm:git:$MirrorUrl.git</connection>
    |    <url>$MirrorUrl</url>
    |  </scm>
    |  $dependencies
    |</project>
  """.trimMargin()

  private companion object {
    const val MirrorUrl: String = "https://github.com/Neckar-IT/neckar-open"
    const val VersionedDependency: String =
      "<dependencies><dependency><groupId>it.neckar.open</groupId><artifactId>unit</artifactId><version>11.0.1</version></dependency></dependencies>"
    const val VersionlessDependency: String =
      "<dependencies><dependency><groupId>it.neckar.open.meistercharts</groupId><artifactId>meistercharts-core</artifactId></dependency></dependencies>"
  }
}
