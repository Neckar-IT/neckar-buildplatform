package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import org.gradle.api.Plugin
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.Properties

/**
 * Keeps the plugin registrations of `internal/buildplatform/conventions/build.gradle.kts` and the `it.neckar.*` constants in [BuildplatformPlugins] together.
 * `java-gradle-plugin` writes one `META-INF/gradle-plugins/<id>.properties` per registration into the main resources.
 */
class BuildplatformPluginRegistryTest {

  private val classLoader: ClassLoader = BuildplatformPluginRegistryTest::class.java.classLoader

  @Test
  fun `every own constant has a descriptor whose implementation class is a Plugin`() {
    val constants: List<PluginConstant> = ownPluginConstants()
    assertThat(constants).isNotEmpty()
    val descriptors: List<PluginDescriptor> = resolveOwnDescriptors()

    val failures: List<String> = constants.mapNotNull { constant ->
      val descriptor: PluginDescriptor = descriptors.find { it.id == constant.id }
        ?: return@mapNotNull "BuildplatformPlugins.${constant.name} = ${constant.id} has no descriptor $DescriptorDirectory/${constant.id}$DescriptorSuffix"
      val implementationClass: Class<*> = runCatching { Class.forName(descriptor.implementationClass, false, classLoader) }
        .getOrElse { return@mapNotNull "${constant.id}: implementation class ${descriptor.implementationClass} does not load: $it" }
      when {
        Plugin::class.java.isAssignableFrom(implementationClass) -> null
        else -> "${constant.id}: implementation class ${descriptor.implementationClass} is no Plugin"
      }
    }

    assertThat(failures).isEmpty()
  }

  @Test
  fun `every descriptor has a constant`() {
    val constantIds: Set<String> = ownPluginConstants().map { it.id }.toSet()
    val descriptors: List<PluginDescriptor> = resolveOwnDescriptors()
    assertThat(descriptors).isNotEmpty()

    val failures: List<String> = descriptors
      .filterNot { it.id in constantIds }
      .map { "${it.id} (${it.implementationClass}) has no constant in BuildplatformPlugins" }

    assertThat(failures).isEmpty()
  }

  private data class PluginConstant(val name: String, val id: String)

  private data class PluginDescriptor(val id: String, val implementationClass: String)

  private fun ownPluginConstants(): List<PluginConstant> {
    return BuildplatformPlugins::class.java.declaredFields
      .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && it.type == String::class.java }
      .map { PluginConstant(name = it.name, id = it.get(null) as String) }
      .filter { it.id.startsWith(OwnIdPrefix) }
  }

  /**
   * Own resources lie on the classpath as a directory; descriptors of foreign plugins come inside jars and stay out.
   */
  private fun resolveOwnDescriptors(): List<PluginDescriptor> {
    return classLoader.getResources(DescriptorDirectory).toList()
      .filter { it.protocol == "file" }
      .flatMap { directory -> File(directory.toURI()).listFiles().requireNotNull { "$directory is no readable directory" }.toList() }
      .filter { it.name.endsWith(DescriptorSuffix) }
      .map { readDescriptor(it) }
  }

  private fun readDescriptor(file: File): PluginDescriptor {
    val implementationClass: String = file.inputStream().use { Properties().apply { load(it) } }
      .getProperty("implementation-class")
      .requireNotNull { "$file carries no implementation-class" }
    return PluginDescriptor(id = file.name.removeSuffix(DescriptorSuffix), implementationClass = implementationClass)
  }

  companion object {
    private const val DescriptorDirectory: String = "META-INF/gradle-plugins"
    private const val DescriptorSuffix: String = ".properties"
    private const val OwnIdPrefix: String = "it.neckar."
  }
}
