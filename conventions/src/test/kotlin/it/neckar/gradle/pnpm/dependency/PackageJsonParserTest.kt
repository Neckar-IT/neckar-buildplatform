package it.neckar.gradle.pnpm.dependency

import assertk.*
import assertk.assertions.*
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for [PackageJsonParser].
 *
 * The repository's own manifests are checked by the `verifyPnpmWorkspaceDependencies` task, which
 * parses all of them; these tests cover the shapes that must not reach it.
 */
class PackageJsonParserTest {

  private val parser = PackageJsonParser()

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `name is the declared package name`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/typescript-react-auth",
        "version": "1.0.0"
      }
    """.trimIndent())

    val packageName = parser.parse(packageJsonFile).name

    assertThat(packageName).isEqualTo(NpmPackageName("@neckar-hub/typescript-react-auth"))
  }

  @Test
  fun `name is null when the manifest declares none`() {
    val packageJsonFile = createPackageJson("""
      {
        "version": "1.0.0"
      }
    """.trimIndent())

    val packageName = parser.parse(packageJsonFile).name

    assertThat(packageName).isNull()
  }

  @Test
  fun `workspaceDependencies extracts from dependencies section`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:*",
          "@neckar-hub/typescript-react-utils": "workspace:*",
          "react": "19.1.0",
          "axios": "1.13.0"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(2)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-utils"))
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-react-utils"))
    assertThat(workspaceDeps).doesNotContain(NpmPackageName("react"))
    assertThat(workspaceDeps).doesNotContain(NpmPackageName("axios"))
  }

  @Test
  fun `workspaceDependencies extracts from devDependencies section`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "devDependencies": {
          "@neckar-hub/oxlint-config": "workspace:*",
          "@neckar-hub/typescript-config": "workspace:*",
          "vitest": "4.0.0"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(2)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/oxlint-config"))
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-config"))
    assertThat(workspaceDeps).doesNotContain(NpmPackageName("vitest"))
  }

  @Test
  fun `workspaceDependencies extracts from both sections`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:*"
        },
        "devDependencies": {
          "@neckar-hub/oxlint-config": "workspace:*"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(2)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-utils"))
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/oxlint-config"))
  }

  @Test
  fun `workspaceDependencies handles empty dependencies`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {},
        "devDependencies": {}
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).isEmpty()
  }

  @Test
  fun `workspaceDependencies handles missing dependencies sections`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project"
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).isEmpty()
  }

  @Test
  fun `workspaceDependencies handles workspace caret specifier`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:^"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(1)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-utils"))
  }

  @Test
  fun `workspaceDependencies handles workspace tilde specifier`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:~"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(1)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-utils"))
  }

  @Test
  fun `workspaceDependencies deduplicates dependencies appearing in both sections`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/my-project",
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:*"
        },
        "devDependencies": {
          "@neckar-hub/typescript-utils": "workspace:*"
        }
      }
    """.trimIndent())

    val workspaceDeps = parser.parse(packageJsonFile).workspaceDependencies.all

    assertThat(workspaceDeps).hasSize(1)
    assertThat(workspaceDeps).contains(NpmPackageName("@neckar-hub/typescript-utils"))
  }

  @Test
  fun `scriptNames lists the declared scripts`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/some-frontend",
        "scripts": {
          "dev": "vite",
          "build": "vite build"
        }
      }
    """.trimIndent())

    assertThat(parser.parse(packageJsonFile).scriptNames).containsExactlyInAnyOrder("dev", "build")
  }

  @Test
  fun `scriptNames is empty when none are declared`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/some-library"
      }
    """.trimIndent())

    assertThat(parser.parse(packageJsonFile).scriptNames).isEmpty()
  }

  @Test
  fun `parse reads name, scripts and workspace dependencies in one go`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/some-frontend",
        "scripts": {
          "build": "vite build"
        },
        "dependencies": {
          "@neckar-hub/typescript-utils": "workspace:*",
          "react": "19.0.0"
        },
        "devDependencies": {
          "@neckar-hub/vite-config": "workspace:*"
        }
      }
    """.trimIndent())

    val parsed = parser.parse(packageJsonFile)

    assertThat(parsed.name).isEqualTo(NpmPackageName("@neckar-hub/some-frontend"))
    assertThat(parsed.scriptNames).containsExactlyInAnyOrder("build")
    assertThat(parsed.script("build")).isEqualTo("vite build")
    assertThat(parsed.workspaceDependencies.dependencies).containsExactly(NpmPackageName("@neckar-hub/typescript-utils"))
    assertThat(parsed.workspaceDependencies.devDependencies).containsExactly(NpmPackageName("@neckar-hub/vite-config"))
    assertThat(parsed.workspaceDependencies.all).containsExactlyInAnyOrder(
      NpmPackageName("@neckar-hub/typescript-utils"),
      NpmPackageName("@neckar-hub/vite-config"),
    )
  }

  @Test
  fun `parse throws for a missing package json`() {
    assertFailure { parser.parse(tempDir.resolve("does-not-exist.json").toFile()) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().contains("does-not-exist.json")
  }

  @Test
  fun `parse names the file when the json is malformed`() {
    val packageJsonFile = createPackageJson("""{ "name": "@neckar-hub/broken", """)

    assertFailure { parser.parse(packageJsonFile) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains("Malformed package.json")
        contains(packageJsonFile.absolutePath)
      }
  }

  @Test
  fun `parse names the file when the manifest is not an object`() {
    assertFailure { parser.parse(createPackageJson("[]")) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains("The manifest")
        contains("must be an object")
        contains("JsonArray")
      }
  }

  @Test
  fun `parse names the field when a dependency version is not a string`() {
    val packageJsonFile = createPackageJson("""
      {
        "name": "@neckar-hub/some-frontend",
        "dependencies": {
          "@neckar-hub/typescript-utils": { "version": "1.0.0" }
        }
      }
    """.trimIndent())

    assertFailure { parser.parse(packageJsonFile) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains("the version of '@neckar-hub/typescript-utils' in 'dependencies'")
        contains(packageJsonFile.absolutePath)
        contains("must be a string")
      }
  }

  @Test
  fun `parse names the field when name is not a string`() {
    assertFailure { parser.parse(createPackageJson("""{ "name": 42 }""")) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains("'name'")
        contains("must be a string")
      }
  }

  @Test
  fun `hardcoded versions are every specifier that names a version rather than a catalog, a workspace package or a local path`() {
    val packageJsonFile = createPackageJson("""
      {
        "dependencies": { "react": "catalog:", "left-pad": "1.3.0" },
        "devDependencies": { "@neckar-hub/typescript-utils": "workspace:*", "vite": "catalog:build", "local": "link:../local", "tarball": "file:./x.tgz", "aliased": "npm:react@19.0.0" },
        "peerDependencies": { "react-dom": "^19.0.0" }
      }
    """.trimIndent())

    assertThat(parser.parse(packageJsonFile).hardcodedVersionDependencies).containsExactly(
      "dependencies left-pad=1.3.0",
      "devDependencies aliased=npm:react@19.0.0",
      "peerDependencies react-dom=^19.0.0",
    )
  }

  private fun createPackageJson(content: String): File {
    val file = tempDir.resolve("package.json").toFile()
    file.writeText(content)
    return file
  }
}
