package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Executes the given block for the given task
 */
fun Project.withTask(name: String, block: (Task) -> Unit) {
  tasks.findByName(name)?.let(block) ?: tasks.whenTaskAdded {
    if (this.name == name) {
      block(this)
    }
  }
}
