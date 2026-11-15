package it.neckar.gradle.verify

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test

class VerifyMainClassExistsPluginTest {

  @Test
  fun `uses the application mainClass when set`() {
    assertThat(
      resolveMainClassNameToVerify(":service", "com.example.AppMainKt")
    ).isEqualTo("com.example.AppMainKt")
  }

  @Test
  fun `fails when application mainClass is missing — no guessed default`() {
    assertFailure {
      resolveMainClassNameToVerify(":service", null)
    }.isInstanceOf(IllegalStateException::class)
      .messageContains("application.mainClass")
  }
}
