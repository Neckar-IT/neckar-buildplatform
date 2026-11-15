package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.util.DefaultLocale

@DefaultLocale("en-US")
class StringCaseTest {
  @Test
  fun testToCamelCase() {
    assertThat("asdf".toCamelCase()).isEqualTo("asdf")
    assertThat("hello_world".toCamelCase()).isEqualTo("helloWorld")
    assertThat("_hello_world".toCamelCase()).isEqualTo("helloWorld")
    assertThat("hello_world_".toCamelCase()).isEqualTo("helloWorld")
    assertThat("hello__world".toCamelCase()).isEqualTo("helloWorld")
    assertThat("Hello_World".toCamelCase()).isEqualTo("helloWorld")
    assertThat("helloWorld".toCamelCase()).isEqualTo("helloWorld")
    assertThat("".toCamelCase()).isEqualTo("")
    assertThat("hello".toCamelCase()).isEqualTo("hello")
    assertThat("HELLO".toCamelCase()).isEqualTo("hELLO")

    assertThat("HELLO_WORLD".toCamelCase()).isEqualTo("hELLOWORLD")

    assertThat("ANOTHER_FEATURE".toCamelCase()).isEqualTo("aNOTHERFEATURE")

    assertThat("__".toCamelCase()).isEqualTo("")
    assertThat("____".toCamelCase()).isEqualTo("")
    assertThat("A_B_C_D".toCamelCase()).isEqualTo("aBCD")
    assertThat("THIS_IS_A_TEST_CASE".toCamelCase()).isEqualTo("tHISISATESTCASE")
  }
}
