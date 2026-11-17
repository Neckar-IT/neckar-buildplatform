package it.neckar.gradle.artifact

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArtifactKeyTest {

  @TempDir
  lateinit var directory: File

  @Test
  fun `a key rejects uppercase and a shortened hash`() {
    assertFailure { ArtifactKey.parse("in-E9E27F39C3E9795866BF15B04D1493A1BB22E1238B476AFFE95571F033FC7597") }.isInstanceOf<IllegalArgumentException>()
    assertFailure { ArtifactKey.parse("in-e9e27f39") }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun `the report holds the key of its inputs and reads it back`() {
    val inputs = ArtifactInputs(listOf(ArtifactInput.Setting("target", "ubuntu")))

    ArtifactKeyReport.write(directory, inputs)

    assertThat(directory.resolve(ArtifactKeyReport.InputsFileName).readText()).isEqualTo("setting:target=ubuntu\n")
    assertThat(ArtifactKeyReport.readKey(directory)).isEqualTo(inputs.key)
  }

  @Test
  fun `a line break, a control character, a name with an equals sign and an absolute label fail`() {
    assertFailure { ArtifactInput.Setting("target", "a\nb") }.messageContains("control character")
    assertFailure { ArtifactInput.File("a\u0000b", FileContentHash(Sha256("0".repeat(64)))) }.messageContains("control character")
    assertFailure { ArtifactInput.Setting("a=b", "c") }.messageContains("holds a '='")
    assertFailure { ArtifactInput.Reference("a=b", ArtifactKey.parse("in-" + "0".repeat(64))) }.messageContains("holds a '='")
    assertFailure { ArtifactInput.Directory("/srv/app") }.messageContains("absolute path")
  }

  @Test
  fun `the text of every kind of input parses back to the same inputs`() {
    val inputs = ArtifactInputs(
      listOf(
        ArtifactInput.Setting("arg.BASE", "a=b"),
        ArtifactInput.Reference("context.base", ArtifactKey.parse("in-" + "1".repeat(64))),
        ArtifactInput.Directory("dist"),
        ArtifactInput.File("dist/a=b.html", FileContentHash(Sha256("2".repeat(64)))),
      )
    )

    assertThat(ArtifactInputs.parse(inputs.serialization)).isEqualTo(inputs)
    assertFailure { ArtifactInputs.parse("unknown:line\n") }.messageContains("Unknown artifact input line")
  }
}
