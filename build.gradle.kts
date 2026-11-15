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

subprojects {
  group = "it.neckar.buildplatform"
  version = buildplatformVersion

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
      url.set("$buildplatformMirrorUrl/tree/main/${project.projectDir.relativeTo(rootDir).invariantSeparatorsPath}")

      licenses {
        license {
          name.set("GPLv3 with Classpath Exception")
          url.set("https://www.gnu.org/software/classpath/license.html")
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
        connection.set("scm:git:$buildplatformMirrorUrl.git")
        developerConnection.set("scm:git:$buildplatformMirrorUrl.git")
        url.set(buildplatformMirrorUrl)
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
