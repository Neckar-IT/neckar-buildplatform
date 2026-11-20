package it.neckar.gradle

import org.gradle.api.tasks.testing.Test

/** The directory below the build directory that holds the crash reports of the test JVMs, one directory per task. */
const val JvmCrashReportsDirectoryName: String = "jvm-crashes"

/** HotSpot's default name of a crash report, `hs_err_pid<pid>.log`, kept for the redirected one. */
const val JvmCrashReportFilePrefix: String = "hs_err_pid"

/** The crash reports directory of the test task [taskName], relative to its project directory. */
fun jvmCrashReportsPath(taskName: String): String = "build/$JvmCrashReportsDirectoryName/$taskName"

/**
 * Sends the crash report of a test JVM to [jvmCrashReportsPath]; HotSpot writes it into the working directory otherwise, which no report collects.
 */
fun Test.redirectJvmCrashReportsToBuildDirectory() {
  // Relative to the working directory, the project directory: the cache key stays the same in every checkout.
  val crashReportsDirectory: String = jvmCrashReportsPath(name)
  jvmArgs("-XX:ErrorFile=$crashReportsDirectory/$JvmCrashReportFilePrefix%p.log")
  // HotSpot falls back to the working directory when the directory of ErrorFile is missing.
  doFirst { workingDir.resolve(crashReportsDirectory).mkdirs() }
}
