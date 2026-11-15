package it.neckar.gradle

import com.github.gradle.node.npm.task.NpmSetupTask
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeSetupTask
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

/**
 * Kotlin/JS linking runs in-process in the Gradle daemon (NoIsolationWorkerFactory), so every link task feeds off its -Xmx4G, not off
 * kotlin.daemon.jvmargs. Eight at once (org.gradle.workers.max) exhaust that heap in the dead-code-elimination phase; measurements in
 * `docs/gradle/daemon-memory.md`.
 */
private const val MaxParallelKotlinJsLinks: Int = 2

/**
 * Runs one task of each node, npm and pnpm task type at a time and at most [MaxParallelKotlinJsLinks] Kotlin/JS links at once, across
 * the build; a subtype such as `PnpmInstallTask` shares the limit of its base type.
 */
fun Project.limitParallelNodeTasksAndKotlinJsLinks() {
  avoidParallelRunningOfTasks<NpxTask>()
  avoidParallelRunningOfTasks<NpmTask>()
  avoidParallelRunningOfTasks<NpmSetupTask>()
  avoidParallelRunningOfTasks<PnpmTask>()
  avoidParallelRunningOfTasks<NodeSetupTask>()
  limitParallelRunningOfTasks<KotlinJsIrLink>(MaxParallelKotlinJsLinks)
}
