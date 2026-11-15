package it.neckar.gradle

import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Lists all svg files within the given folder
 */
fun File.listSvgFilesRecursively(): List<File> {
  val svgFiles = mutableListOf<File>()

  listFiles(FileFilter { it.isDirectory })?.map {
    svgFiles.addAll(it.listSvgFilesRecursively())
  }

  //Add the files from this directory itself
  svgFiles.addAll(listSvgFilesInDirectory())

  return svgFiles
}

private fun File.listSvgFilesInDirectory(): List<File> {
  return listFiles(".svg")
}

/**
 * Lists all files with the given suffix within this directory
 */
fun File.listFiles(suffix: String): List<File> {
  return listFiles { _, name -> name.lowercase(Locale.getDefault()).endsWith(suffix) }
    ?.sortedBy { it.name }
    ?: throw FileNotFoundException("Could not find source folder <$absolutePath>")
}

/**
 * Returns the base names
 */
fun List<File>.baseNames(): List<String> {
  return map {
    it.nameWithoutExtension
  }
}

/**
 * Creates a new child file
 */
fun File.child(path: String): File {
  return File(this, path)
}
