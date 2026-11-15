package it.neckar.gradle.ksp.generating.ts

/**
 * Offers access to predefined TypeScript declaration files
 */
object PredefinedTypeScriptDeclarationFiles {
  /**
   * Contains the declaration file for the Kotlin standard lib classes
   */
  val kotlinStdlibDeclaration: PredefinedDTsFile = PredefinedDTsFile(
    name = "kotlin-kotlin-stdlib.d.ts",
    content = PredefinedTypeScriptDeclarationFiles::class.java.getResource("kotlin-kotlin-stdlib.d.ts")!!.readText()
  )

  /**
   * Contains the re-xport of the UUID class, necessary since Kotlin exports the classes with mangeld names
   */
  val kotlinNativeTypesTs: PredefinedDTsFile = PredefinedDTsFile(
    name = "neckarIT-kotlin-native-types.ts",
    content = PredefinedTypeScriptDeclarationFiles::class.java.getResource("neckarIT-kotlin-native-types.ts")!!.readText()
  )
}

data class PredefinedDTsFile(
  val name: String,
  val content: String,
)
