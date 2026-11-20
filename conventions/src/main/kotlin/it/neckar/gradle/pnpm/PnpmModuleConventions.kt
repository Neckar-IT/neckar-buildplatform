package it.neckar.gradle.pnpm

import com.github.gradle.node.NodeExtension
import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeSetupTask
import it.neckar.gradle.AnsiConsole
import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.FileAppendingOutputStream
import it.neckar.gradle.ansiConsole
import it.neckar.gradle.npmVersion
import it.neckar.gradle.parsePackageJson
import it.neckar.gradle.pnpm.dependency.PackageJsonParser
import it.neckar.gradle.pnpm.dependency.PackageNameRegistryService
import it.neckar.gradle.pnpm.dependency.PnpmWorkspaceDependencyResolver
import it.neckar.gradle.pnpm.dependency.declareWorkspaceDependencyInputs
import it.neckar.gradle.readCapturedOutput
import it.neckar.gradle.requireDirExists
import it.neckar.gradle.requireDirectoryEmpty
import it.neckar.projects.GradleProjectPath
import kotlinx.serialization.json.jsonObject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File

/** The task of a pnpm module that runs `pnpm run build:noDeps`, whose `dist/` a frontend delivers. */
const val PnpmRunBuildTaskName: String = "pnpmRunBuild"

/**
 * What a build hands to the conventions of its pnpm modules.
 *
 * [sharedTypescriptConfigDirectory] holds the shared TypeScript, lint and bundle configs the packages read as raw source through
 * workspace symlinks; [additionalWorkspaceInputs] are further files of the workspace root the package builds read;
 * [excludedSourceDirectory] names a directory inside a module that another task writes, so it is no input of `pnpmRunBuild`.
 */
class PnpmModuleSettings(
  val sharedTypescriptConfigDirectory: (Project) -> File,
  val additionalWorkspaceInputs: (Project) -> FileCollection = { project -> project.files() },
  val excludedSourceDirectory: (Project) -> Provider<File> = { project -> project.provider { null } },
)

/** The pnpm module a call of [configurePnpmModule] configured. */
class PnpmModule(
  val pnpmRunBuild: TaskProvider<PnpmTask>,
  val packageNameRegistryService: Provider<PackageNameRegistryService>,
  /** The names of the scripts of the module's `package.json`. */
  val scriptNames: Set<String>,
)

/**
 * Applies the node plugin, pointed at the one node and pnpm installation of the root project ([NodeToolchainLayout]), on the root
 * project and on every pnpm module.
 */
private fun Project.configurePnpmToolchain() {
  plugins.apply(BuildplatformPlugins.Base)
  plugins.apply(BuildplatformPlugins.Node)

  listOf("npmInstall", "npmSetup", "yarn", "yarnSetup").forEach {
    tasks.named(it).configure {
      enabled = false
    }
  }

  extensions.getByType<NodeExtension>().let { nodeExtension ->
    val toolchainOwner = rootProject
    nodeExtension.workDir.set(toolchainOwner.layout.projectDirectory.dir(NodeToolchainLayout.NodeDistributionPath))
    nodeExtension.yarnWorkDir.set(toolchainOwner.layout.buildDirectory.dir(NodeToolchainLayout.YarnInstallPath))
    nodeExtension.pnpmWorkDir.set(toolchainOwner.layout.buildDirectory.dir(NodeToolchainLayout.pnpmInstallPath))
    nodeExtension.npmWorkDir.set(toolchainOwner.layout.buildDirectory.dir(NodeToolchainLayout.npmInstallPath))

    nodeExtension.download = true
    nodeExtension.version = npmVersion("node")
    nodeExtension.pnpmVersion = npmVersion("pnpm")
  }

  val cleanPaths = NodeToolchainLayout.cleanPaths(GradleProjectPath(path))
  tasks.named<Delete>("clean") {
    delete(cleanPaths)
  }

  tasks.register("printPnpmVersion") {
    val execOperations: ExecOperations = serviceOf<ExecOperations>()
    val moduleDirectory: File = projectDir
    val console: AnsiConsole = ansiConsole

    doLast {
      logger.lifecycle("PNPM: ${console.green(execOperations.pnpmVersion(moduleDirectory))}")
    }
  }

  tasks.register("verifyPnpmVersion") {
    val execOperations: ExecOperations = serviceOf<ExecOperations>()
    val moduleDirectory: File = projectDir
    val expectedPnpmVersionProvider: Provider<String> = project.extensions.getByType<NodeExtension>().pnpmVersion
    val console: AnsiConsole = ansiConsole

    doLast {
      val actualPnpmVersion = execOperations.pnpmVersion(moduleDirectory)
      val expectedPnpmVersion = expectedPnpmVersionProvider.get()

      logger.lifecycle("            Actual | Expected")
      logger.lifecycle("------------------------------------------------------------")

      if (actualPnpmVersion == expectedPnpmVersion) {
        logger.lifecycle(console.blue("PNPM: ✓ ${console.green(actualPnpmVersion)}"))
      } else {
        logger.lifecycle("PNPM:      ${console.orange("✗")} ${console.orange(actualPnpmVersion)} | ${console.blue(expectedPnpmVersion.trim())}")
        logger.lifecycle("Execute ${console.blue("tools/nvm/install-pnpm.sh")}")
      }
    }
  }
}

/**
 * Configures the root project as the owner of the node and pnpm installation and of the one `pnpm install` of the workspace.
 */
fun Project.configurePnpmToolchainOwner() {
  require(NodeToolchainLayout.ownsInstallation(GradleProjectPath(path))) {
    "This method must only be called on the toolchain owner ${NodeToolchainLayout.OwnerProjectPath}, but got $path"
  }

  configurePnpmToolchain()

  tasks.named("pnpmInstall").configure {
    doNotTrackState("pnpm manages the state itself")
  }
}

/**
 * Configures a pnpm module other than the root project: its `pnpm install` is the one of the root project, `pnpmRunBuild` runs
 * `build:noDeps` after the `pnpmRunBuild` of each workspace dependency and is part of `assemble`, `pnpmLintReport` of `check`.
 * [pnpmProjects] are the pnpm modules of the build, whose `package.json` names the `workspace:` dependencies resolve against.
 */
fun Project.configurePnpmModule(pnpmProjects: List<GradleProjectPath>, settings: PnpmModuleSettings): PnpmModule {
  require(NodeToolchainLayout.ownsInstallation(GradleProjectPath(path)).not()) {
    "This method must not be called on the toolchain owner $path — use configurePnpmToolchainOwner"
  }
  val packageNameRegistryService = PackageNameRegistryService.register(this, provider { pnpmProjects })

  // A module converted from a Kotlin module keeps no Kotlin or Java sources.
  listOf(
    "src/commonMain/kotlin", "src/jsMain/kotlin", "src/jvmMain/kotlin", "src/jvmTest/resources", "src/jsTest/resources",
    "src/main/kotlin", "src/main/java", "src/main/resources", "src/test/kotlin", "src/test/java", "src/test/resources",
  ).forEach { requireDirectoryEmpty(it) }

  configurePnpmToolchain()

  val warningTask = tasks.register("pnpmInstall - do not use") {
    group = "Pnpm"
    description = "use pnpmInstall at root project instead"

    val console: AnsiConsole = ansiConsole

    doLast {
      logger.lifecycle(console.yellow("-------------------------------------------------------------------"))
      logger.lifecycle(console.yellow("pnpmInstall is disabled for this project. Use :pnpmInstall instead."))
      logger.lifecycle(console.yellow("-------------------------------------------------------------------"))
    }
  }

  val verifyProjectConfiguration = tasks.register("verifyProjectConfiguration") {
    description = "Verifies the configuration of the project"

    // A package without TypeScript has no tsconfig.json: an empty one would mean `include: **/*`.
    val packageJson = parsePackageJson().jsonObject
    val declaresTypeScript: Boolean = listOf("dependencies", "devDependencies")
      .any { packageJson[it]?.jsonObject?.containsKey("typescript") == true }
    val tsConfig: File = file("tsconfig.json")
    val console: AnsiConsole = ansiConsole

    doLast {
      if (declaresTypeScript.not()) {
        return@doLast
      }

      if (tsConfig.isFile.not()) {
        logger.error(console.red("Missing ${tsConfig.absolutePath}. A tsconfig.json file is required for each PNPM project that depends on typescript."))
        throw GradleException("Expected ${tsConfig.absolutePath} to be a file")
      }
    }
  }

  tasks.named("pnpmInstall").configure {
    dependsOn(":pnpmInstall", warningTask, verifyProjectConfiguration)
    enabled = false
  }

  // The module runs the binaries of the root project; the disabled setup tasks keep the dependency on the owner's installation.
  tasks.named("pnpmSetup").configure {
    dependsOn(":pnpmSetup", verifyProjectConfiguration)
    enabled = false
  }
  tasks.named<NodeSetupTask>("nodeSetup").configure {
    dependsOn(":nodeSetup", verifyProjectConfiguration)
    enabled = false
  }

  val packageScripts: Set<String> = PackageJsonParser().parse(file("package.json")).scriptNames

  val pnpmRunBuild: TaskProvider<PnpmTask> = tasks.register<PnpmTask>(PnpmRunBuildTaskName) {
    description = "Executes `pnpm run build:noDeps` (the complete per-package verification unit), " +
      "falling back to `pnpm run build:only` for packages not yet migrated to the build:noDeps contract"

    dependsOn(":pnpmInstall", verifyProjectConfiguration)

    val workspaceDependencyProjects = PnpmWorkspaceDependencyResolver(packageNameRegistryService.get().registry).resolveWorkspaceDependencyProjects(project)

    workspaceDependencyProjects.projects.forEach { dependencyProject ->
      dependsOn(dependencyProject.tasks.named(PnpmRunBuildTaskName))
    }

    val hasBuildNoDeps = packageScripts.contains("build:noDeps")
    val hasBuildOnly = packageScripts.contains("build:only")
    val buildScript = if (hasBuildNoDeps) "build:noDeps" else "build:only"
    // `bundle` is the leaf that writes `dist/`; a package on the `build:noDeps` contract without it only verifies.
    val producesDist = hasBuildNoDeps.not() || packageScripts.contains("bundle")

    onlyIf {
      hasBuildNoDeps || hasBuildOnly
    }

    args = listOf("run", buildScript)

    // The inputs are complete on purpose: a missing input leaves a stale bundle up to date. The run writes the excluded directories itself.
    val excludedSourceDirectory: Provider<File> = settings.excludedSourceDirectory(project)
    inputs.files(
      project.fileTree(project.projectDir) {
        exclude("dist/**", "build/**", "node_modules/**", ".astro/**", ".gradle/**")
        exclude("test-results/**", "playwright-report/**", "coverage/**")
        exclude("tests/playwright/**/.auth/**")
        exclude { element -> excludedSourceDirectory.orNull?.let { element.file.toPath().startsWith(it.toPath()) } == true }
      }
    ).withPropertyName("packageSources").withPathSensitivity(PathSensitivity.RELATIVE)

    // Matched by config file type: a broad tree would capture files these packages generate into their own directory.
    inputs.files(
      project.fileTree(settings.sharedTypescriptConfigDirectory(project)) {
        include("**/*.json", "**/*.json5", "**/*.ts", "**/*.mts", "**/*.cts")
        include("**/*.js", "**/*.mjs", "**/*.cjs", "**/*.css")
        exclude("**/dist/**", "**/build/**", "**/node_modules/**", "**/.gradle/**", "**/.astro/**")
      }
    ).withPropertyName("sharedTypescriptConfig").withPathSensitivity(PathSensitivity.RELATIVE)

    // The workspace files pnpm, tsc and oxlint open; the root oxlint and oxfmt configs are not read (`--disable-nested-config`).
    inputs.files(
      project.rootProject.file("pnpm-lock.yaml"),
      project.rootProject.file("pnpm-workspace.yaml"),
      project.rootProject.file("package.json"),
      project.rootProject.file("tsconfig.json"),
      project.rootProject.file(".gitignore"),
      settings.additionalWorkspaceInputs(project),
    ).withPropertyName("workspaceRootConfig").withPathSensitivity(PathSensitivity.RELATIVE)

    // The node-gradle toolchain runs the task, so its versions rebuild the bundle when they change.
    inputs.property("nodeVersion", project.npmVersion("node"))
    inputs.property("pnpmVersion", project.npmVersion("pnpm"))

    declareWorkspaceDependencyInputs(workspaceDependencyProjects)

    if (producesDist) {
      outputs.dir("dist").withPropertyName("distOutput")
    }

    outputs.cacheIf("pnpmRunBuild produces a deterministic dist bundle") { true }

    val nodeModulesDir: File = file("node_modules")
    val distDir: File = file("dist")
    val console: AnsiConsole = ansiConsole

    doFirst {
      nodeModulesDir.requireDirExists { file ->
        "Expected ${file?.absolutePath} to be a directory. Maybe :pnpmInstall didn't run?"
      }
    }

    doLast {
      if (producesDist.not()) {
        return@doLast
      }

      if (distDir.isDirectory.not()) {
        logger.error(console.red("Expected ${distDir.absolutePath} to be a directory"))
        throw GradleException("Expected ${distDir.absolutePath} to be a directory")
      }

      // Counted recursively: a `tsc` package may put every artifact below a single directory.
      val fileCount = distDir.walkTopDown().count { it.isFile }

      // A single-file bundle (`vite-plugin-singlefile`) emits `index.html` alone.
      val expectedMinFileCount = 1
      if (fileCount < expectedMinFileCount) {
        logger.error(console.red("Expected ${distDir.absolutePath} to contain at least $expectedMinFileCount files. But only got $fileCount"))
        throw GradleException("Expected ${distDir.absolutePath} to contain at least $expectedMinFileCount files. But only got $fileCount")
      }
    }
  }

  tasks.named("assemble") {
    dependsOn(pnpmRunBuild)
  }

  // Writes the GitLab Code Quality JSON of oxlint; the lint gate itself is `lint:check` inside `build:noDeps`.
  val pnpmLintReport = tasks.register<PnpmTask>("pnpmLintReport") {
    description = "Executes `pnpm run lint:report` and writes a GitLab Code Quality JSON report"
    group = "Pnpm"

    dependsOn(":pnpmInstall", pnpmRunBuild)

    val hasLintReportScript: Boolean = packageScripts.contains("lint:report")
    onlyIf {
      hasLintReportScript
    }

    // `--silent` keeps pnpm's header lines out of the captured JSON.
    args = listOf("run", "--silent", "lint:report")

    // oxlint exits non-zero on findings; `lint:check` in `build:noDeps` gates on them.
    ignoreExitValue = true

    val reportFile = layout.buildDirectory.file("reports/oxlint/gitlab.json")
    outputs.file(reportFile)

    val stdOutFile: Provider<RegularFile> = layout.buildDirectory.file("reports/oxlint/stdout.txt")
    captureOutput(stdOutFile)

    doLast {
      val raw = stdOutFile.get().asFile.readCapturedOutput()
      val jsonStart = raw.indexOf('[')
      val json = if (jsonStart >= 0) raw.substring(jsonStart) else "[]"
      reportFile.get().asFile.writeText(json)
    }
  }

  tasks.named("check") {
    dependsOn(pnpmLintReport)
  }

  tasks.register<PnpmTask>("pnpmLintFix") {
    description = "Executes `pnpm run lint --fix`"
    group = "Pnpm"

    dependsOn(":pnpmInstall", pnpmRunBuild)

    val hasLintScript: Boolean = packageScripts.contains("lint")
    onlyIf {
      hasLintScript
    }

    args = listOf("run", "lint", "--fix")
  }

  tasks.register<PnpmTask>("pnpmFormat") {
    description = "Executes `pnpm run format`"
    group = "Pnpm"

    dependsOn(":pnpmInstall", pnpmRunBuild)

    val hasFormatScript: Boolean = packageScripts.contains("format")
    onlyIf {
      hasFormatScript
    }

    args = listOf("run", "format")

    // The output is printed only on failure, so the exit value is handled in doLast.
    ignoreExitValue = true

    val stdOutFile: Provider<RegularFile> = layout.buildDirectory.file("reports/pnpm-format/stdout.txt")
    val stdErrFile: Provider<RegularFile> = layout.buildDirectory.file("reports/pnpm-format/stderr.txt")
    captureOutput(stdOutFile, stdErrFile)

    doLast {
      val exitValue = result?.exitValue ?: 0
      if (exitValue != 0) {
        val stdOutContent = stdOutFile.get().asFile.readCapturedOutput()
        val stdErrContent = stdErrFile.get().asFile.readCapturedOutput()

        if (stdOutContent.isNotBlank()) {
          logger.error("stdout:\n$stdOutContent")
        }
        if (stdErrContent.isNotBlank()) {
          logger.error("stderr:\n$stdErrContent")
        }

        logger.error("pnpmFormat failed with exit code $exitValue")
        throw GradleException("pnpmFormat failed with exit code $exitValue")
      }
    }
  }

  tasks.register<PnpmTask>("pnpmFormatFix") {
    description = "Executes `pnpm run format:fix`"
    group = "Pnpm"

    dependsOn(":pnpmInstall", pnpmRunBuild)

    val hasFormatFixScript: Boolean = packageScripts.contains("format:fix")
    onlyIf {
      hasFormatFixScript
    }

    args = listOf("run", "format:fix")
  }

  return PnpmModule(pnpmRunBuild = pnpmRunBuild, packageNameRegistryService = packageNameRegistryService, scriptNames = packageScripts)
}

/**
 * Sends stdout, and stderr if [stdErrFile] is given, to files, because the configuration cache stores no stream. Every run
 * deletes the files first, so [readCapturedOutput] reads only this run's output.
 */
private fun PnpmTask.captureOutput(stdOutFile: Provider<RegularFile>, stdErrFile: Provider<RegularFile>? = null) {
  execOverrides {
    standardOutput = FileAppendingOutputStream(stdOutFile.get().asFile)
    if (stdErrFile != null) {
      errorOutput = FileAppendingOutputStream(stdErrFile.get().asFile)
    }
  }

  doFirst {
    listOfNotNull(stdOutFile, stdErrFile).forEach { outputFile ->
      outputFile.get().asFile.parentFile.mkdirs()
      outputFile.get().asFile.delete()
    }
  }
}

/** The version `pnpm --version` prints in [directory], where pnpm picks it from the `packageManager` field of the package.json. */
private fun ExecOperations.pnpmVersion(directory: File): String {
  val output = ByteArrayOutputStream()
  exec {
    workingDir = directory
    commandLine("pnpm", "--version")
    standardOutput = output
  }
  return output.toString(Charsets.UTF_8).trim()
}
