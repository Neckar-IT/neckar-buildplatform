package it.neckar.projects.sharing

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class SharedFileKindTest {
  private enum class Example : SharedFileKind {
    DnsZones,
  }

  @Test
  fun `the usage is the kebab-case name below it_neckar_shared`() {
    assertThat(Example.DnsZones.usage).isEqualTo("it.neckar.shared.dns-zones")
  }

  @Test
  fun `the elements configuration is the lowerCamelCase name with the suffix Elements`() {
    assertThat(Example.DnsZones.elementsConfigurationName).isEqualTo("dnsZonesElements")
  }
}
