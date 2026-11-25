// The root build script of the build platform applies MavenCentralPublishingPlugin, which the project `conventions` publishes:
// a build applies no plugin one of its own projects builds, so this build compiles the package from the sources of `conventions`.
plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}

kotlin {
  sourceSets.named("main") {
    kotlin.srcDir("../conventions/src/main/kotlin")
    kotlin.include("it/neckar/gradle/publishing/**")
  }
}
