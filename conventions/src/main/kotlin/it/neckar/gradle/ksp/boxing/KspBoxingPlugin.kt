package it.neckar.gradle.ksp.boxing

import it.neckar.gradle.ksp.BaseKspPlugin
import it.neckar.projects.common.MavenCoordinate

/** Adds the boxing-verifier KSP processor. */
class KspBoxingPlugin : BaseKspPlugin() {
  override val kspProcessor: MavenCoordinate = MavenCoordinate("it.neckar.open.ksp", "boxing-verifier")
}
