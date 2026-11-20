package it.neckar.open.generated

import assertk.*
import assertk.assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GeneratedFileCommentSyntaxTest {
  @ParameterizedTest
  @CsvSource(
    "Main.kt, DoubleSlash",
    "build.gradle.kts, DoubleSlash",
    "index.ts, DoubleSlash",
    "index.d.ts, DoubleSlash",
    "Widget.tsx, DoubleSlash",
    "script.js, DoubleSlash",
    "script.mjs, DoubleSlash",
    "config.json5, DoubleSlash",
    "run.sh, NumberSign",
    "run.bash, NumberSign",
    "local.env, NumberSign",
    "compose.yml, NumberSign",
    "services.yaml, NumberSign",
    "nginx.conf, NumberSign",
    "gradle.properties, NumberSign",
    "packages.txt, NumberSign",
    "authorized_keys, NumberSign",
    ".nvmrc, NumberSign",
    "index.html, XmlComment",
    "status.svg, XmlComment",
    "pom.xml, XmlComment",
    "README.md, XmlComment",
  )
  fun forFileName(fileName: String, expected: GeneratedFileCommentSyntax) {
    assertThat(GeneratedFileCommentSyntax.forFileName(fileName)).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource("package.json", "hosts.tsv", "Dockerfile", "image.png", "authorized_keys.bak")
  fun namesOutsideTheTableGetNone(fileName: String) {
    assertThat(GeneratedFileCommentSyntax.forFileName(fileName)).isNull()
  }
}
