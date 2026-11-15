/*
 * Copyright (C) 2013-2026 Neckar IT GmbH, Mössingen, Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules, regardless of
 * the license terms of these independent modules, and to copy and distribute
 * the resulting combined work under terms of your choice, provided that every
 * copy of the combined work is accompanied by a complete copy of the source
 * code of this library.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package it.neckar.gradle.ksp.options

/**
 * Contains (all) KSP plugin options for all plugins and projects.
 * Published in `it.neckar.buildplatform:ksp-contract`, which the KSP conventions and the symbol processors both depend on
 */
object KspPluginOptions {
  object Common {
    /**
     * The project path of the project that is currently executed
     */
    val ProjectPath: KspProcessingParameterName = KspProcessingParameterName("projectPath")

    /**
     * The project base dir
     */
    val ProjectDir: KspProcessingParameterName = KspProcessingParameterName("projectDir")

  }

  object TypeScriptDefinitionGeneration {
    /**
     * The name of the file that contains the generated TypeScript definitions
     */
    val TypeScriptDefinitionFileName: KspProcessingParameterName = KspProcessingParameterName("typeScriptDefinitionFileName")

    /**
     * Specifies the file path that contains the list of types by projects
     */
    val TypesListForProjectCollectionFilePath: KspProcessingParameterName = KspProcessingParameterName("typesListForProjectCollectionFilePath")
  }

  object TypesListGenerator {
    /**
     * Argument for KSP process (read in `it.neckar.ksp.types.list.generator.TypesListGeneratorProcessor`)
     */
    val PackageNamesListFileName: KspProcessingParameterName = KspProcessingParameterName("PackageNamesListFileName")

    /**
     * Argument for KSP process (read in `it.neckar.ksp.types.list.generator.TypesListGeneratorProcessor`)
     */
    val ClassNamesListFileName: KspProcessingParameterName = KspProcessingParameterName("ClassNamesListFileName")
  }

  object SpecHarvest {
  }
}
