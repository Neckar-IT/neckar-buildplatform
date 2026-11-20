@file:OptIn(ComposeKotlinGradlePluginApi::class)

package it.neckar.gradle

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.ComposeKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.resources.KotlinTargetResourcesPublication

/** Task group of every check and verification task; `./gradlew tasks` lists them under `Verification tasks`. */
const val VerificationGroup: String = LifecycleBasePlugin.VERIFICATION_GROUP

/** Task group of the tasks that assemble the outputs of a module; `./gradlew tasks` lists them under `Build tasks`. */
const val BuildGroup: String = LifecycleBasePlugin.BUILD_GROUP

/** The lifecycle task every verification of a module hangs on. */
const val CheckTaskName: String = LifecycleBasePlugin.CHECK_TASK_NAME

const val ProcessResourcesTaskName: String = JavaPlugin.PROCESS_RESOURCES_TASK_NAME

const val MainSourceSetName: String = SourceSet.MAIN_SOURCE_SET_NAME

const val MainCompilationName: String = KotlinCompilation.MAIN_COMPILATION_NAME

/** Key of the extra property under which the Kotlin Gradle plugin registers [KotlinTargetResourcesPublication]. */
const val KotlinTargetResourcesExtensionName: String = KotlinTargetResourcesPublication.EXTENSION_NAME

val UsageAttribute: Attribute<Usage> = Usage.USAGE_ATTRIBUTE

val CategoryAttribute: Attribute<Category> = Category.CATEGORY_ATTRIBUTE
