package it.neckar.gradle.publishing

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The rules by which Maven Central rejects a deployment, checked against a Maven repository directory.
 * Free of Gradle types, so a unit test covers each rule with a directory.
 */
object MavenCentralPublicationRules {
  private const val PomSuffix: String = ".pom"
  private const val SignatureSuffix: String = ".asc"

  /** Files of a version directory that are no artifact of their own and need no signature. */
  private val UnsignedSuffixes: List<String> = listOf(SignatureSuffix, ".md5", ".sha1", ".sha256", ".sha512")

  /** Checks every POM below [repository]; a repository without any POM is the one violation that names no POM. */
  fun check(repository: File): MavenCentralPublicationReport {
    val poms: List<File> = repository.walkTopDown().filter { it.isFile && it.name.endsWith(PomSuffix) }.sortedBy { it.path }.toList()
    if (poms.isEmpty()) {
      return MavenCentralPublicationReport(emptyList(), listOf("no POM below $repository"))
    }

    val relativePaths: List<String> = poms.map { it.relativeTo(repository).invariantSeparatorsPath }
    val violations: List<String> = poms.zip(relativePaths).flatMap { (pom, relativePath) ->
      (pomViolations(pom) + fileViolations(pom)).map { "$relativePath $it" }
    }
    return MavenCentralPublicationReport(relativePaths, violations)
  }

  /**
   * The coordinates `<groupId>:<artifactId>` of every dependency of [pom] without a version, in `dependencies` and in `dependencyManagement`.
   * Maven Central rejects the whole deployment for one of them.
   */
  fun dependenciesWithoutVersion(pom: File): List<String> = dependenciesWithoutVersion(parse(pom))

  private fun dependenciesWithoutVersion(project: Element): List<String> {
    val dependencyLists: List<Element?> = listOf(project.child("dependencies"), project.child("dependencyManagement").child("dependencies"))
    return dependencyLists.flatMap { it.children("dependency") }
      .filter { it.text("version").isEmpty() }
      .map { "${it.text("groupId")}:${it.text("artifactId")}" }
  }

  private fun parse(pom: File): Element =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().apply { setErrorHandler(ThrowingErrorHandler) }.parse(pom).documentElement

  /** The default handler prints a parse error to stderr before the parser throws it. */
  private object ThrowingErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) = Unit
    override fun error(exception: SAXParseException): Unit = throw exception
    override fun fatalError(exception: SAXParseException): Unit = throw exception
  }

  /** The violations of the POM itself. */
  private fun pomViolations(pom: File): List<String> {
    val project: Element = try {
      parse(pom)
    } catch (e: SAXException) {
      return listOf("is no well-formed XML: ${e.message}")
    }

    return buildList {
      if (project.text("version").isEmpty()) {
        add("has no version")
      }
      listOf("name", "description", "url").filter { project.text(it).isEmpty() }.forEach { add("has no $it") }

      if (project.child("licenses").children("license").none { it.text("name").isNotEmpty() }) {
        add("has no license with a name")
      }
      if (project.child("developers").children("developer").none { it.text("name").isNotEmpty() }) {
        add("has no developer with a name")
      }

      val scm: Element? = project.child("scm")
      listOf("url", "connection").filter { scm.text(it).isEmpty() }.forEach { add("has no scm $it") }

      dependenciesWithoutVersion(project).forEach { add("has the dependency $it without a version") }
    }
  }

  /**
   * The violations of the files beside the POM: the sources and javadoc jars and the signatures.
   * The file names start with the name of the POM, which holds a timestamp for a `-SNAPSHOT` version.
   */
  private fun fileViolations(pom: File): List<String> {
    val directory: File = pom.parentFile
    val stem: String = pom.name.removeSuffix(PomSuffix)

    return buildList {
      if (directory.resolve("$stem.jar").isFile) {
        listOf("sources", "javadoc")
          .filterNot { directory.resolve("$stem-$it.jar").isFile }
          .forEach { add("has no $stem-$it.jar beside it") }
      }

      directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("maven-metadata").not() && UnsignedSuffixes.none(it.name::endsWith) }
        .filterNot { directory.resolve(it.name + SignatureSuffix).isFile }
        .sortedBy { it.name }
        .forEach { add("has no signature ${it.name}$SignatureSuffix") }
    }
  }

  /** The first direct child element named [name]; null for a null receiver. */
  private fun Element?.child(name: String): Element? = children(name).firstOrNull()

  /** Every direct child element named [name]. */
  private fun Element?.children(name: String): List<Element> {
    val nodes = this?.childNodes ?: return emptyList()
    return (0 until nodes.length).map(nodes::item).filterIsInstance<Element>().filter { it.tagName == name }
  }

  /** The trimmed text of the direct child [name]; empty when the child is absent. */
  private fun Element?.text(name: String): String = child(name)?.textContent?.trim().orEmpty()
}

/**
 * The result of [MavenCentralPublicationRules.check]: [poms] holds the path of each POM relative to the repository, and the violation
 * of a POM starts with that path, e.g. `it/neckar/open/unit/11.0.1/unit-11.0.1.pom has no description`.
 */
data class MavenCentralPublicationReport(val poms: List<String>, val violations: List<String>)
