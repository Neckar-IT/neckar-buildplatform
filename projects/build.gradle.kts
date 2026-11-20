plugins {
  `kotlin-dsl`
  kotlin("plugin.serialization")
}

description = "The registry types of a Gradle project registry: project paths, task paths, project types, roles and Kotlin targets."

dependencies {
  api(libs.kotlinx.serialization.core)

  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.junit.jupiter.api)
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
