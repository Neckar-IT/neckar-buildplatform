package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * Configures the toolchain using the [JvmType] - as configured in the project.
 */
fun Project.configureToolchain(jvmType: JvmType) {
  configureToolchain(jvmType.languageVersion)
}

/**
 * Java 25 is a LTS version
 * See https://en.wikipedia.org/wiki/Java_version_history for details
 */
fun Project.configureToolchainJava25LTS(): Provider<JavaCompiler> {
  return configureToolchain(JvmType.JavaLatestLTS.languageVersion)
}

/**
 * Configures the toolchain
 */
fun Project.configureToolchain(javaLanguageVersion: JavaLanguageVersion, vendor: JvmVendorSpec = DefaultJvmVendorSpec.any(), implementation: JvmImplementation? = null): Provider<JavaCompiler> {
  configure<JavaPluginExtension> {
    toolchain {
      configureJavaToolchain(javaLanguageVersion, vendor, implementation)
    }
  }

  val javaPluginExtension = extensions.getByType(JavaPluginExtension::class.java)
  val javaToolChainService = extensions.getByType(JavaToolchainService::class.java)
  val javaCompiler = javaToolChainService.compilerFor {
    languageVersion = javaLanguageVersion
  }

  tasks.withType<JavaCompile>().configureEach {
    this.javaCompiler = javaCompiler
  }
  tasks.withType<JavaExec>().configureEach {
    this.javaLauncher = javaToolChainService.launcherFor(javaPluginExtension.toolchain)
  }

  extensions.findByName("kotlin")?.let {
    (it as KotlinProjectExtension).jvmToolchain {
      configureJavaToolchain(javaLanguageVersion, vendor, implementation)
    }
  }

  //Set the SDK based upon the language level
  extensions.findByType<org.gradle.plugins.ide.idea.model.IdeaModel>()?.let {
    it.module {
      jdkName = "${javaLanguageVersion.asInt()}"
      languageLevel = IdeaLanguageLevel(javaLanguageVersion.asInt())
    }
  }

  return javaCompiler
}

/**
 * Configures the java tool chain
 */
fun JavaToolchainSpec.configureJavaToolchain(javaLanguageVersion: JavaLanguageVersion, vendor: JvmVendorSpec, implementation: JvmImplementation?) {
  languageVersion = javaLanguageVersion
  this.vendor = vendor
  implementation?.let {
    this.implementation = it
  }
}
