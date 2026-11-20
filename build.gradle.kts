// The Kotlin projects are compiled with Gradle's embedded Kotlin, like build-logic/ of the monorepo, which depends on them.
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("plugin.serialization") version embeddedKotlinVersion apply false
  kotlin("jvm") version embeddedKotlinVersion apply false
  alias(libs.plugins.com.vanniktech.maven.publish) apply false
}

/** One version for every artifact of the build platform, released together by the tag `buildplatform-<version>`. */
val buildplatformVersion: String = layout.settingsDirectory.file("buildplatform.version").asFile.readText().trim()

/** The public read-only mirror of every file git tracks under internal/buildplatform; `main` holds the newest release. */
val buildplatformMirrorUrl: String = "https://github.com/Neckar-IT/neckar-buildplatform"

/** The public read-only mirror of every file git tracks under internal/open, the sources of the registries of the open projects. */
val openMirrorUrl: String = "https://github.com/Neckar-IT/neckar-open"

/**
 * A registry of the open projects that settings.gradle.kts includes from internal/open. Group and license stay those of internal/open:
 * MeisterCharts is Apache-licensed. The group it.neckar.open of the open libraries is released by the tag `open-<version>` alone.
 */
class OpenRegistry(val name: String, val group: String, val apacheLicensed: Boolean)

val openRegistries: List<OpenRegistry> = listOf(
  OpenRegistry("meistercharts-projects", "it.neckar.open.meistercharts.project-registry", apacheLicensed = true),
  OpenRegistry("open-projects", "it.neckar.open.project-registry", apacheLicensed = false),
)

subprojects {
  val openRegistry: OpenRegistry? = openRegistries.firstOrNull { it.name == name }
  group = openRegistry?.group ?: "it.neckar.buildplatform"
  version = buildplatformVersion
  val mirrorUrl: String = if (openRegistry == null) buildplatformMirrorUrl else openMirrorUrl
  val mirrorRoot: File = if (openRegistry == null) rootDir else rootDir.resolve("../open")

  if (openRegistry != null) {
    // The build scripts of the registries name the artifacts they depend on without a version, as their own builds do; this build produces them.
    // The published POM names the version the resolution picked, the one of this build.
    configurations.configureEach {
      resolutionStrategy.dependencySubstitution {
        substitute(module("it.neckar.buildplatform:projects")).using(project(":projects"))
        substitute(module("it.neckar.open.meistercharts.project-registry:meistercharts-projects")).using(project(":meistercharts-projects"))
      }
    }
    pluginManager.withPlugin("maven-publish") {
      extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
          versionMapping {
            allVariants {
              fromResolutionResult()
            }
          }
        }
      }
    }
    // The registries share their directories with their own builds in the composite of the monorepo.
    layout.buildDirectory.set(rootDir.resolve("build/open-registries/$name"))
  }

  // Every project publishes signed to Maven Central; the plugin picks the publication from the project's plugins
  // (version catalog, Kotlin JVM library, Gradle plugin with its markers).
  apply(plugin = "com.vanniktech.maven.publish")
  extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
    publishToMavenCentral()
    signAllPublications()

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
      name.set(project.name)
      description.set(project.provider { project.description })
      url.set("$mirrorUrl/tree/main/${project.projectDir.relativeTo(mirrorRoot.canonicalFile).invariantSeparatorsPath}")

      licenses {
        if (openRegistry?.apacheLicensed == true) {
          license {
            name.set("Apache License 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        } else {
          license {
            name.set("GPLv3 with Classpath Exception")
            url.set("https://www.gnu.org/software/classpath/license.html")
          }
        }
      }
      developers {
        developer {
          id.set("jschneider")
          name.set("Johannes Schneider")
          email.set("js@neckar.it")
        }
      }

      scm {
        connection.set("scm:git:$mirrorUrl.git")
        developerConnection.set("scm:git:$mirrorUrl.git")
        url.set(mirrorUrl)
      }
    }
  }

  pluginManager.withPlugin("java-base") {
    extensions.configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
      }
    }
  }

  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    compilerOptions {
      javaParameters = true
    }
  }

  tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"

    // redirectJvmCrashReportsToBuildDirectory() of conventions, which this build compiles rather than applies.
    val crashReportsDirectory = "build/jvm-crashes/$name"
    jvmArgs("-XX:ErrorFile=$crashReportsDirectory/hs_err_pid%p.log")
    doFirst { workingDir.resolve(crashReportsDirectory).mkdirs() }
  }
}
