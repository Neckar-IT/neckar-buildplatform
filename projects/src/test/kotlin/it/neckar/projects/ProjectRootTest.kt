package it.neckar.projects

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Exercises the lookups of [ProjectRoot] against a purpose-built registry, so an assertion does not
 * depend on the current project list.
 */
class ProjectRootTest {
  @TempDir
  lateinit var projectDir: File

  @Test
  fun `find resolves a registered project by path`() {
    val registry = TestRegistry()

    assertThat(registry.find(":test:jvm")).isEqualTo(registry.test.jvm)
    assertThat(registry.find(GradleProjectPath(":test:jvm"))).isEqualTo(registry.test.jvm)
  }

  @Test
  fun `find resolves a registered project by gradle project`() {
    val registry = TestRegistry()
    val root = ProjectBuilder.builder().withName("root").withProjectDir(projectDir).build()
    val intermediate = ProjectBuilder.builder().withName("test").withParent(root).build()
    val subProject = ProjectBuilder.builder().withName("jvm").withParent(intermediate).build()

    assertThat(registry.find(subProject)).isEqualTo(registry.test.jvm)
    assertThat(registry.findOrNull(subProject)).isEqualTo(registry.test.jvm)
  }

  @Test
  fun `find throws for an unregistered path`() {
    assertFailure { TestRegistry().find(":test:not-registered") }
      .messageContains(":test:not-registered")
  }

  @Test
  fun `findOrNull returns null for an unregistered path`() {
    assertThat(TestRegistry().findOrNull(":test:not-registered")).isNull()
  }

  @Test
  fun `registering the same path twice is rejected`() {
    assertFailure { DuplicatePathRegistry().findOrNull(":test") }
      .messageContains("already configured")
  }

  @Test
  fun `projects are grouped by type`() {
    val registry = TestRegistry()
    val test = registry.test

    assertThat(registry.jvmProjects()).containsExactlyInAnyOrder(test.jvm)
    assertThat(registry.kspProcessorProjects()).containsExactlyInAnyOrder(test.ksp)
    assertThat(registry.multiplatformProjects()).containsExactlyInAnyOrder(test.multiplatform, test.jvmOnly, test.nativeOnly)
    assertThat(registry.pythonProjects()).containsExactlyInAnyOrder(test.python)
    assertThat(registry.ideaPluginProjects()).containsExactlyInAnyOrder(test.ideaPlugin)
    assertThat(registry.intermediateProjects()).containsExactlyInAnyOrder(test, test.intermediate)
    assertThat(registry.parents()).containsExactlyInAnyOrder(test.parent)
    assertThat(registry.otherProjects()).containsExactlyInAnyOrder(test.other)
  }

  /**
   * `pnpmProjects()` matches on the sealed supertype, so every dev-server strategy has to be
   * included — a filter on a single subtype would silently drop the web apps.
   */
  @Test
  fun `pnpmProjects covers every pnpm dev server strategy`() {
    val test = TestRegistry().test

    assertThat(TestRegistry().pnpmProjects().map { it.path }).containsExactlyInAnyOrder(test.pnpm.path, test.vite.path, test.astro.path)
  }

  @Test
  fun `projectsAtOrBelow selects a subtree`() {
    val registry = TestRegistry()

    assertThat(registry.projectsAtOrBelow(GradleProjectPath(":test")).size).isEqualTo(registry.allProjects.size)
    assertThat(registry.projectsAtOrBelow(GradleProjectPath(":test:jvm"))).containsExactlyInAnyOrder(registry.test.jvm)
  }

  @Test
  fun `a multiplatform project carries its registered targets`() {
    val test = TestRegistry().test

    assertThat(test.multiplatform.targets).containsExactlyInAnyOrder(KotlinTarget.Jvm, KotlinTarget.Js)
    assertThat(test.jvmOnly.targets).containsExactlyInAnyOrder(KotlinTarget.Jvm)
    assertThat(test.nativeOnly.targets).containsExactlyInAnyOrder(KotlinTarget.LinuxX64)
  }

  /**
   * `hasJvmTarget` decides toolchain, JUnit and Kover, so a native-only module must not report it.
   */
  @Test
  fun `hasJvmTarget follows the registered target set`() {
    val test = TestRegistry().test

    assertThat(test.multiplatform.hasJvmTarget).isTrue()
    assertThat(test.jvmOnly.hasJvmTarget).isTrue()
    assertThat(test.nativeOnly.hasJvmTarget).isFalse()
    assertThat(test.jvm.hasJvmTarget).isFalse()
  }

  @Test
  fun `only multiplatform projects carry targets`() {
    val test = TestRegistry().test

    assertThat(test.jvm.targets).isEmpty()
    assertThat(test.python.targets).isEmpty()
    assertThat(test.parent.targets).isEmpty()
  }

  @Test
  fun `a multiplatform project without a target is rejected`() {
    assertFailure { NoTargetRegistry() }
      .messageContains("must declare at least one Kotlin target")
  }

  @Test
  fun `a container carries the container role`() {
    val test = TestRegistry().test

    assertThat(test.intermediate.role).isEqualTo(ProjectRole.Container)
    assertThat(test.parent.role).isEqualTo(ProjectRole.Container)
    assertThat(test.vite.role).isEqualTo(ProjectRole.Frontend)
  }

  @Test
  fun `a container with another role is rejected`() {
    assertFailure { MisroledContainerRegistry() }
      .messageContains("must carry the role Container")
  }

  @Test
  fun `a duplicate target is rejected`() {
    assertFailure { DuplicateTargetRegistry() }
      .messageContains("duplicate Kotlin target")
  }

  @Test
  fun `containsByPath compares by path`() {
    val registry = TestRegistry()
    val jvmProjects = registry.jvmProjects()

    assertThat(jvmProjects.containsByPath(":test:jvm")).isTrue()
    assertThat(jvmProjects.containsByPath(GradleProjectPath(":test:jvm"))).isTrue()
    assertThat(jvmProjects.containsByPath(":test:python")).isFalse()
  }

  @Test
  fun `properties named after their directories pass the naming check`() {
    assertThat(TestRegistry().accessorNameViolations()).isEmpty()
  }

  @Test
  fun `a property named differently from its directory is a violation`() {
    assertThat(MisnamedRegistry().accessorNameViolations())
      .containsExactlyInAnyOrder(":test:kotlin-lang: property 'lang' must be named 'kotlinLang'")
  }
}

/**
 * A registry covering every [ProjectType]. [ProjectType.KotlinMultiplatform] appears three times
 * with different target sets — the type does not encode the platforms, so one entry could not
 * represent it.
 */
private class TestRegistry : ProjectRoot() {
  val test: TestGroup = TestGroup(this)

  class TestGroup(parent: ConfiguredProject) : ConfiguredProject(parent, GradleProjectPath(":test"), ProjectType.Intermediate, ProjectRole.Container) {
    val jvm: ConfiguredProject = jvm("jvm", ProjectRole.Library)
    val ksp: ConfiguredProject = kspProcessor("ksp", ProjectRole.Library)
    val multiplatform: ConfiguredProject = multiplatform("multiplatform", ProjectRole.Library, KotlinTarget.Jvm, KotlinTarget.Js)
    val jvmOnly: ConfiguredProject = multiplatform("jvm-only", ProjectRole.Library, KotlinTarget.Jvm)
    val nativeOnly: ConfiguredProject = multiplatform("native-only", ProjectRole.Library, KotlinTarget.LinuxX64)
    val pnpm: ConfiguredProject = pnpm("pnpm", ProjectRole.Library)
    val vite: ConfiguredProject = vite("vite", ProjectRole.Frontend)
    val astro: ConfiguredProject = astro("astro", ProjectRole.Library)
    val python: ConfiguredProject = python("python", ProjectRole.Library)
    val ideaPlugin: ConfiguredProject = ideaPlugin("idea-plugin", ProjectRole.Library)
    val intermediate: ConfiguredProject = intermediate("intermediate")
    val parent: ConfiguredProject = parent("parent")
    val other: ConfiguredProject = other("other", ProjectRole.Library)
  }
}

private class DuplicatePathRegistry : ProjectRoot() {
  val first: ConfiguredProject = intermediate("test")
  val second: ConfiguredProject = intermediate("test")
}

private class NoTargetRegistry : ProjectRoot() {
  val noTarget: ConfiguredProject = multiplatform("no-target", ProjectRole.Library)
}

private class MisroledContainerRegistry : ProjectRoot() {
  val group: Group = Group(this)

  class Group(parent: ConfiguredProject) : ConfiguredProject(parent, GradleProjectPath(":group"), ProjectType.Intermediate, ProjectRole.Library)
}

private class DuplicateTargetRegistry : ProjectRoot() {
  val duplicateTarget: ConfiguredProject = multiplatform("duplicate-target", ProjectRole.Library, KotlinTarget.Jvm, KotlinTarget.Jvm)
}

private class MisnamedRegistry : ProjectRoot() {
  val test: TestGroup = TestGroup(this)

  class TestGroup(parent: ConfiguredProject) : ConfiguredProject(parent, GradleProjectPath(":test"), ProjectType.Intermediate, ProjectRole.Container) {
    val lang: ConfiguredProject = jvm("kotlin-lang", ProjectRole.Library)
  }
}
