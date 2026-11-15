package it.neckar.gradle.shadow

import org.gradle.api.GradleException
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Finds provider classes that a fat jar's provider registries name but neither the jar nor the JDK contains.
 *
 * Shadow's minimizer follows bytecode references only. A provider that is named in
 * `META-INF/services/<service type>`, `META-INF/javamail.providers` or
 * `META-INF/javamail.default.providers` alone looks unused and is dropped, while the registry entry
 * stays. The gap shows only when the registry is read: `ServiceLoader` throws
 * `java.util.ServiceConfigurationError: ... Provider ... not found`, JavaMail's `Session` a
 * `NoSuchProviderException`. [FatJarMetadataPlugin] turns every finding into a build failure.
 *
 * A registry is checked only when a class that reads it lies in the jar or comes from the JDK: its
 * service type for `META-INF/services`, `Session` for JavaMail.
 */
object FatJarServiceProviders {
  /**
   * Throws a [GradleException] naming every missing provider of [fatJar], otherwise returns normally.
   */
  fun verify(fatJar: File) {
    val missingProviders: List<MissingServiceProvider> = findMissing(fatJar)

    if (missingProviders.isEmpty()) {
      return
    }

    throw GradleException(
      "${fatJar.name} lacks service provider classes its registries name (${missingProviders.size}):\n" +
        missingProviders.joinToString(separator = "\n") { missingProvider -> "  ${missingProvider.registry} -> ${missingProvider.providerClassName} is missing in ${fatJar.name}" } +
        "\nShadow's minimizer dropped the provider because no bytecode references it; reading the registry fails at runtime " +
        "with a ServiceConfigurationError (META-INF/services) or a NoSuchProviderException (JavaMail). Exclude the library that ships the provider from minimization in the minimize block of the fat jar task."
    )
  }

  /**
   * Returns every provider entry of [fatJar] whose class neither the jar nor the JDK contains, ordered by registry path and line.
   */
  fun findMissing(fatJar: File): List<MissingServiceProvider> {
    return ZipFile(fatJar).use { zipFile ->
      val fileEntries: List<ZipEntry> = zipFile.entries().asSequence().filter { zipEntry -> zipEntry.isDirectory.not() }.toList()
      val entryNames: Set<String> = fileEntries.map { zipEntry -> zipEntry.name }.toSet()

      fun jarContains(className: String): Boolean = classEntryName(className) in entryNames

      fun isAvailable(className: String): Boolean = jarContains(className) || isJdkClass(className)

      RegistryFormat.entries
        .flatMap { format ->
          fileEntries
            .filter { zipEntry -> format.matches(zipEntry.name) }
            .filter { zipEntry -> format.readerClasses(zipEntry.name).any { readerClass -> isAvailable(readerClass) } }
            .flatMap { zipEntry ->
              format.providerClassNames(zipFile.readLines(zipEntry))
                .filter { providerClassName -> isAvailable(providerClassName).not() }
                .map { providerClassName -> MissingServiceProvider(zipEntry.name, providerClassName) }
            }
        }
        .sortedBy { missingProvider -> missingProvider.registry }
    }
  }

  private fun ZipFile.readLines(zipEntry: ZipEntry): List<String> {
    return getInputStream(zipEntry).bufferedReader().use { reader -> reader.readLines() }
  }

  /**
   * The jar entry that holds the class with the binary name [className], e.g. `io/netty/Foo$Bar.class`.
   */
  private fun classEntryName(className: String): String = "${className.replace('.', '/')}.class"

  /**
   * Whether the JDK provides [className], as it does for service types like `java.sql.Driver`.
   */
  private fun isJdkClass(className: String): Boolean {
    return ClassLoader.getPlatformClassLoader().getResource(classEntryName(className)) != null
  }

  /**
   * A kind of registry: which jar entries it is, which classes read it at runtime, and how its lines name providers.
   */
  private enum class RegistryFormat {
    /**
     * `META-INF/services/<service type>`, read by `ServiceLoader` for the service type; one provider per line, `#` starts a comment.
     */
    Services {
      override fun matches(entryName: String): Boolean {
        return entryName.startsWith(ServicesDirectory) && entryName.removePrefix(ServicesDirectory).contains('/').not()
      }

      override fun readerClasses(registry: String): List<String> = listOf(registry.removePrefix(ServicesDirectory))

      override fun providerClassNames(lines: List<String>): List<String> {
        return lines.map { line -> line.substringBefore('#').trim() }.filter { providerClassName -> providerClassName.isNotEmpty() }
      }
    },

    /**
     * `META-INF/javamail.providers` and `META-INF/javamail.default.providers`, read by `Session` of `javax.mail` or its
     * successor `jakarta.mail`; each line names its provider as `class=<name>;`.
     */
    JavaMail {
      override fun matches(entryName: String): Boolean = entryName in JavaMailRegistries

      override fun readerClasses(registry: String): List<String> = JavaMailSessionClasses

      override fun providerClassNames(lines: List<String>): List<String> {
        return lines
          .filter { line -> line.trimStart().startsWith("#").not() }
          .mapNotNull { line -> JavaMailProviderClassPattern.find(line)?.groupValues?.get(1) }
      }
    };

    /**
     * Whether the jar entry [entryName] is a registry of this format.
     */
    abstract fun matches(entryName: String): Boolean

    /**
     * The classes that read [registry] at runtime; a registry none of them is available for is never read.
     */
    abstract fun readerClasses(registry: String): List<String>

    /**
     * The binary names of the provider classes that [lines] of a registry name.
     */
    abstract fun providerClassNames(lines: List<String>): List<String>

    companion object {
      private const val ServicesDirectory: String = "META-INF/services/"

      private val JavaMailRegistries: List<String> = listOf("META-INF/javamail.providers", "META-INF/javamail.default.providers")

      private val JavaMailSessionClasses: List<String> = listOf("javax.mail.Session", "jakarta.mail.Session")

      private val JavaMailProviderClassPattern: Regex = Regex("""(?:^|;)\s*class\s*=\s*([^;\s]+)""")
    }
  }
}

/**
 * A provider class that [registry] names but neither the fat jar nor the JDK contains.
 */
data class MissingServiceProvider(
  /**
   * The path of the registry inside the fat jar, e.g. `META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate`.
   */
  val registry: String,
  /**
   * The binary name of the provider class, e.g. `org.glassfish.jersey.internal.RuntimeDelegateImpl`.
   */
  val providerClassName: String,
)
