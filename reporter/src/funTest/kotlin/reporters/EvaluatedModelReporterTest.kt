/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.readOrtResult

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

class EvaluatedModelReporterTest : WordSpec({
    "EvaluatedModelReporter" should {
        "create the expected JSON output" {
            val expectedResult = File(
                "src/funTest/assets/evaluated-model-reporter-test-expected-output.json"
            ).readText()
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            generateReport(EvaluatedModelJsonReporter(), ortResult).normalizeLineBreaks() shouldBe expectedResult
        }

        "create the expected YAML output" {
            val expectedResult = File(
                "src/funTest/assets/evaluated-model-reporter-test-expected-output.yml"
            ).readText()
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            generateReport(EvaluatedModelYamlReporter(), ortResult) shouldBe expectedResult
        }
    }
})

private fun generateReport(reporter: EvaluatedModelReporter, ortResult: OrtResult) =
    ByteArrayOutputStream().also { outputStream ->
        val resolutionProvider = DefaultResolutionProvider()
        resolutionProvider.add(ortResult.getResolutions())

        val input = ReporterInput(
            ortResult = ortResult,
            resolutionProvider = resolutionProvider
        )

        reporter.generateReport(outputStream, input)
    }.toString("UTF-8")
