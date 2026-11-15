package it.neckar.gradle.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FatJarServiceProvidersTest {
  @Test
  fun `a provider the minimizer dropped is reported`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate" to "org.glassfish.jersey.internal.RuntimeDelegateImpl\n",
      "jakarta/ws/rs/ext/RuntimeDelegate.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).containsExactly(
      MissingServiceProvider("META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate", "org.glassfish.jersey.internal.RuntimeDelegateImpl"),
    )
  }

  @Test
  fun `missing providers are ordered by registry path and line`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/java.sql.Driver" to "org.postgresql.Driver\norg.h2.Driver\n",
      "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate" to "org.glassfish.jersey.internal.RuntimeDelegateImpl\n",
      "jakarta/ws/rs/ext/RuntimeDelegate.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).containsExactly(
      MissingServiceProvider("META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate", "org.glassfish.jersey.internal.RuntimeDelegateImpl"),
      MissingServiceProvider("META-INF/services/java.sql.Driver", "org.postgresql.Driver"),
      MissingServiceProvider("META-INF/services/java.sql.Driver", "org.h2.Driver"),
    )
  }

  @Test
  fun `a provider inside the jar passes`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/io.ktor.client.HttpClientEngineContainer" to "# engines\n\nio.ktor.client.engine.cio.CIOEngineContainer # the CIO engine\n",
      "io/ktor/client/HttpClientEngineContainer.class" to "class bytes",
      "io/ktor/client/engine/cio/CIOEngineContainer.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `a provider class of the JDK passes`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/java.nio.file.spi.FileSystemProvider" to "jdk.nio.zipfs.ZipFileSystemProvider\n",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `a nested provider class is looked up by its binary name`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration" to "io.netty.util.internal.Hidden\$NettyBlockHoundIntegration\n",
      "reactor/blockhound/integration/BlockHoundIntegration.class" to "class bytes",
      "io/netty/util/internal/Hidden\$NettyBlockHoundIntegration.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `a registry whose service type the minimizer dropped as well is never read`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider" to "it.neckar.otel.NeckarRuntimeContextResourceProvider\n",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `a service type of the JDK makes its registry live`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/java.sql.Driver" to "org.postgresql.Driver\n",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).containsExactly(
      MissingServiceProvider("META-INF/services/java.sql.Driver", "org.postgresql.Driver"),
    )
  }

  @Test
  fun `a JavaMail provider the minimizer dropped is reported`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/javamail.providers" to
        "# JavaMail IMAP provider Sun Microsystems, Inc\n" +
        "# protocol=pop3; type=store; class=com.sun.mail.pop3.POP3Store; vendor=Sun Microsystems, Inc;\n" +
        "protocol=imap; type=store; class=com.sun.mail.imap.IMAPStore; vendor=Sun Microsystems, Inc;\n" +
        "protocol=smtp; type=transport; class=com.sun.mail.smtp.SMTPTransport; vendor=Sun Microsystems, Inc;\n",
      "javax/mail/Session.class" to "class bytes",
      "com/sun/mail/smtp/SMTPTransport.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).containsExactly(
      MissingServiceProvider("META-INF/javamail.providers", "com.sun.mail.imap.IMAPStore"),
    )
  }

  @Test
  fun `a JavaMail default provider of jakarta mail the minimizer dropped is reported`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/javamail.default.providers" to "protocol=smtp; type=transport; class=org.eclipse.angus.mail.smtp.SMTPTransport; vendor=Eclipse;\n",
      "jakarta/mail/Session.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).containsExactly(
      MissingServiceProvider("META-INF/javamail.default.providers", "org.eclipse.angus.mail.smtp.SMTPTransport"),
    )
  }

  @Test
  fun `a JavaMail registry without a Session class is never read`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/javamail.providers" to "protocol=imap; type=store; class=com.sun.mail.imap.IMAPStore; vendor=Sun Microsystems, Inc;\n",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `a file in a subdirectory of META-INF services is no registry`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/it/neckar/Service" to "it.neckar.MissingProvider\n",
      "it/neckar/Service.class" to "class bytes",
    )

    assertThat(FatJarServiceProviders.findMissing(fatJar)).isEmpty()
  }

  @Test
  fun `the failure names jar, registry and provider class`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate" to "org.glassfish.jersey.internal.RuntimeDelegateImpl\n",
      "jakarta/ws/rs/ext/RuntimeDelegate.class" to "class bytes",
    )

    val failure = assertThrows<GradleException> {
      FatJarServiceProviders.verify(fatJar)
    }

    assertThat(failure.message.orEmpty()).contains(
      "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate -> org.glassfish.jersey.internal.RuntimeDelegateImpl is missing in fat.jar"
    )
  }

  @Test
  fun `a jar with every provider passes the verification`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate" to "org.glassfish.jersey.internal.RuntimeDelegateImpl\n",
      "jakarta/ws/rs/ext/RuntimeDelegate.class" to "class bytes",
      "org/glassfish/jersey/internal/RuntimeDelegateImpl.class" to "class bytes",
    )

    FatJarServiceProviders.verify(fatJar)
  }

  private fun Path.writeJar(vararg entries: Pair<String, String>): File {
    val fatJar = resolve("fat.jar").toFile()

    ZipOutputStream(fatJar.outputStream()).use { zipOutputStream ->
      entries.forEach { (entryName, content) ->
        zipOutputStream.putNextEntry(ZipEntry(entryName))
        zipOutputStream.write(content.toByteArray())
        zipOutputStream.closeEntry()
      }
    }

    return fatJar
  }
}
