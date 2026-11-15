// Gradle loads gradle/libs.versions.toml of this build as `libs` by convention.

rootProject.name = "buildplatform"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    // The node Gradle plugin the conventions build on is published to the Gradle plugin portal alone.
    gradlePluginPortal()
  }
}

include("version-catalog")
// The registry types every registry of projects builds on.
include("projects")
// The marker of generated files (ADL 0180).
include("generated-file-marker")
// The KSP option names and the types-list model, shared by the KSP conventions and their processors.
include("ksp-contract")
// The convention plugins without operational knowledge.
include("conventions")
