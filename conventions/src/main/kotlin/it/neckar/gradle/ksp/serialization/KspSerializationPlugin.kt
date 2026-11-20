package it.neckar.gradle.ksp.serialization

import it.neckar.projects.common.MavenCoordinate
import it.neckar.gradle.ksp.BaseKspPlugin

/**
 * Adds the serialization-verifier KSP processor.
 */
class KspSerializationPlugin : BaseKspPlugin() {
  override val kspProcessor: MavenCoordinate = MavenCoordinate("it.neckar.open.ksp", "serialization-verifier")
}

