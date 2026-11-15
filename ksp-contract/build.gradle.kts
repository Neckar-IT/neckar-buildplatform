// The KSP option names and the types-list model: the KSP conventions write the model and pass the options, their processors read both.
// A processor depends on this artifact alone, so its KSP classpath stays free of the Gradle API.
plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

description = "The KSP option names and the types-list model shared by the KSP convention plugins and their symbol processors."

dependencies {
  // TypesListForProjectCollection.load reads the JSON the types-list tasks of the conventions write.
  api(libs.kotlinx.serialization.json)
}
