package it.neckar.open.generated

import assertk.*
import assertk.assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GeneratedFileMarkerExamplesTest {
  @ParameterizedTest
  @MethodSource("commentExamples")
  internal fun prependToWritesTheExampleFile(example: GeneratedFileMarkerExample) {
    val marker = GeneratedFileMarker(
      by = example.by.requireNotNullField("by"),
      source = example.source.requireNotNullField("source"),
      regenerateHint = example.regenerateHint.requireNotNullField("hint"),
    )
    val syntax = example.commentSyntax.requireNotNullField("syntax")

    assertThat(marker.prependTo(example.content.requireNotNullField("content"), syntax)).isEqualTo(example.file)
  }

  @ParameterizedTest
  @MethodSource("commentExamples")
  internal fun theFileNameSelectsTheExampleSyntax(example: GeneratedFileMarkerExample) {
    assertThat(GeneratedFileCommentSyntax.forFileName(example.fileName)).isEqualTo(example.commentSyntax)
  }

  @ParameterizedTest
  @MethodSource("allExamples")
  internal fun findGeneratedFileOriginReadsTheExampleOrigin(example: GeneratedFileMarkerExample) {
    val expected = example.by?.let { GeneratedFileOrigin(by = it, source = example.source) }

    assertThat(findGeneratedFileOrigin(example.file)).isEqualTo(expected)
  }

  companion object {
    @JvmStatic
    @JvmName("allExamples")
    internal fun allExamples(): List<GeneratedFileMarkerExample> = GeneratedFileMarkerExample.readAll()

    @JvmStatic
    @JvmName("commentExamples")
    internal fun commentExamples(): List<GeneratedFileMarkerExample> = allExamples().filter { it.commentSyntax != null }

    private fun <T : Any> T?.requireNotNullField(field: String): T = requireNotNull(this) { "the example misses '$field'" }
  }
}
