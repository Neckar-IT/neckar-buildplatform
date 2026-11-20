// The marker of generated files (ADL 0180), consumed by coordinate in Build Logic and the main build.
// Kotlin stdlib only, so that a build outside the monorepo resolves it from Maven Central alone.
plugins {
  kotlin("jvm")
}

description = "The marker of generated files: writes the header naming the generator and finds the origin of a generated file."

dependencies {
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.params)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.assertk.jvm)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform {
    includeEngines("junit-jupiter")
  }

  filter {
    includeTestsMatching("*Test")
    isFailOnNoMatchingTests = true
  }
}
