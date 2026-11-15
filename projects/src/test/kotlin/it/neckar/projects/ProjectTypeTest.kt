package it.neckar.projects

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ProjectTypeTest {

  /**
   * [ProjectType.all] replaced the former enum `entries`, so nothing enforces its completeness at
   * compile time any more: a new `data object` is picked up by `when` exhaustiveness checks but not
   * by the hand-maintained list. Everything driven by [ProjectType.all] silently skips the missing
   * type instead of failing.
   */
  @Test
  fun `all contains every concrete project type`() {
    assertThat(ProjectType.all).containsExactlyInAnyOrder(*concreteProjectTypes().toTypedArray())
  }

  @Test
  fun `all contains no duplicates`() {
    assertThat(ProjectType.all.distinct().size).isEqualTo(ProjectType.all.size)
  }

  @Test
  fun `every pnpm type is a Pnpm subtype and no other type is`() {
    assertThat(ProjectType.all.filter { it is ProjectType.Pnpm })
      .containsExactlyInAnyOrder(ProjectType.Pnpm.Library, ProjectType.Pnpm.Vite, ProjectType.Pnpm.Astro)
  }

  /**
   * Collects the object instance of every concrete (non-sealed) subtype of [ProjectType], walking
   * nested sealed interfaces such as [ProjectType.Pnpm].
   *
   * A subtype that is neither an object nor sealed would contribute nothing and thereby make the
   * completeness check pass while [ProjectType.all] misses it — exactly the defect this test exists
   * to catch, so it fails loudly instead.
   */
  private fun concreteProjectTypes(): List<ProjectType> {
    fun collect(kClass: KClass<out ProjectType>): List<ProjectType> {
      val objectInstance = kClass.objectInstance
      if (objectInstance != null) {
        return listOf(objectInstance)
      }

      val sealedSubclasses = kClass.sealedSubclasses
      check(sealedSubclasses.isNotEmpty()) {
        "${kClass.simpleName} is neither a data object nor a sealed type — this test cannot enumerate it. " +
          "Make it a data object, or extend the enumeration."
      }

      return sealedSubclasses.flatMap { collect(it) }
    }

    return collect(ProjectType::class)
  }
}
