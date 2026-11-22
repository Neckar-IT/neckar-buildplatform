package it.neckar.projects.sharing

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.named
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Publishes every entry of [artifacts] (a file, a directory, or the task producing it) as the consumable configuration of [entry]:
 * `publishFiles(OpenSharedFiles.ScriptMoneyCases, file("src/scriptMoney.cases.json"))`.
 */
fun Project.publishFiles(entry: SharedFileKind, vararg artifacts: Any): NamedDomainObjectProvider<ConsumableConfiguration> {
  return configurations.consumable(entry.elementsConfigurationName) {
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(entry.usage))
      attribute(Category.CATEGORY_ATTRIBUTE, objects.named(entry.usage))
    }
    artifacts.forEach { artifact -> outgoing.artifact(artifact) }
  }
}

/**
 * Registers a resolvable configuration for [entry], named after the property it is delegated to:
 * `val scriptMoneyCases by resolvedFiles(OpenSharedFiles.ScriptMoneyCases)` registers `scriptMoneyCases`, whose dependency names the producer,
 * `scriptMoneyCases(project(Projects.open.commons.scriptMoney))`.
 */
fun Project.resolvedFiles(entry: SharedFileKind): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Configuration>> {
  return PropertyDelegateProvider { _, property ->
    val configuration: Configuration = configurations.create(property.name) {
      isCanBeConsumed = false
      isTransitive = false
      attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(entry.usage))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(entry.usage))
      }
    }
    ReadOnlyProperty { _, _ -> configuration }
  }
}
