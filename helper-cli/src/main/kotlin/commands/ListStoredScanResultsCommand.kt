/*
 * Copyright (C) 2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.expandTilde

internal class ListStoredScanResultsCommand : CliktCommand(
    name = "list-stored-scan-results",
    help = "Lists the provenance of all stored scan results for a given package identifier."
) {
    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan results storage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val packageId by option(
        "--package-id",
        help = "The target package for which the scan results shall be listed."
    ).convert { Identifier(it) }
        .required()

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
                "-P scanner.postgresStorage.schema=testSchema"
    ).convert { Pair(it.substringBefore("="), it.substringAfter("=", "")) }
        .multiple()
        .unique()

    override fun run() {
        val config = OrtConfiguration.load(configArguments.toMap(), configFile)
        ScanResultsStorage.configure(config.scanner ?: ScannerConfiguration())

        println("Searching for scan results of '${packageId.toCoordinates()}' in ${ScanResultsStorage.storage.name}.")

        val scanResults = when (val readResult = ScanResultsStorage.storage.read(packageId)) {
            is Success -> readResult.result
            is Failure -> {
                throw UsageError("Could not read scan results: ${readResult.error}")
            }
        }

        println("Found ${scanResults.results.size} scan results:")

        scanResults.results.forEach { result ->
            println("\n${yamlMapper.writeValueAsString(result.provenance)}")
        }
    }
}
