package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test

class NodeEngineVersionsTest {
  private val packageJson = """
    {
      "name": "neckar-hub",
      "engines": {
        "node": "24.16.0",
        "pnpm": "11.3.0"
      },
      "packageManager": "pnpm@11.3.0",
      "devDependencies": {
        "@types/node": "24.12.4"
      }
    }
  """.trimIndent()

  @Test
  fun `parses engines node version`() {
    assertThat(parseNodeEngineVersion(packageJson)).isEqualTo("24.16.0")
  }

  @Test
  fun `parses engines pnpm version`() {
    assertThat(parsePnpmEngineVersion(packageJson)).isEqualTo("11.3.0")
  }

  @Test
  fun `engines node does not match @types-node when it appears first`() {
    val pkg = """
      {
        "devDependencies": { "@types/node": "24.12.4" },
        "engines": { "node": "24.16.0" }
      }
    """.trimIndent()
    assertThat(parseNodeEngineVersion(pkg)).isEqualTo("24.16.0")
  }

  @Test
  fun `fails on missing engines node`() {
    assertFailure { parseNodeEngineVersion("""{ "engines": { "pnpm": "11.3.0" } }""") }
      .messageContains("engines.node")
  }
}
