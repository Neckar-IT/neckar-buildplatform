plugins {
  `version-catalog`
}

description = "The version catalog of neckar IT: every third-party library, plugin and version its builds pin."

catalog {
  versionCatalog {
    from(files(layout.settingsDirectory.file("gradle/libs.versions.toml")))
  }
}
