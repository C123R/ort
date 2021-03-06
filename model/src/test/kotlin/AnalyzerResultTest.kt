/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor

import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

class AnalyzerResultTest : WordSpec() {
    private val issue1 = OrtIssue(source = "source-1", message = "message-1")
    private val issue2 = OrtIssue(source = "source-2", message = "message-2")
    private val issue3 = OrtIssue(source = "source-3", message = "message-3")
    private val issue4 = OrtIssue(source = "source-4", message = "message-4")

    private val package1 = Package.EMPTY.copy(id = Identifier("type-1", "namespace-1", "package-1", "version-1"))
    private val package2 = Package.EMPTY.copy(id = Identifier("type-2", "namespace-2", "package-2", "version-2"))
    private val package3 = Package.EMPTY.copy(id = Identifier("type-3", "namespace-3", "package-3", "version-3"))

    private val pkgRef1 = package1.toReference(issues = listOf(issue1))
    private val pkgRef2 = package2.toReference(
        dependencies = sortedSetOf(package3.toReference(issues = listOf(issue2)))
    )

    private val scope1 = Scope("scope-1", sortedSetOf(pkgRef1))
    private val scope2 = Scope("scope-2", sortedSetOf(pkgRef2))

    private val project1 = Project.EMPTY.copy(
        id = Identifier("type-1", "namespace-1", "project-1", "version-1"),
        scopes = sortedSetOf(scope1)
    )
    private val project2 = Project.EMPTY.copy(
        id = Identifier("type-2", "namespace-2", "project-2", "version-2"),
        scopes = sortedSetOf(scope1, scope2)
    )

    private val analyzerResult1 = ProjectAnalyzerResult(
        project1, sortedSetOf(package1.toCuratedPackage()),
        listOf(issue3, issue4)
    )
    private val analyzerResult2 = ProjectAnalyzerResult(
        project2,
        sortedSetOf(package1.toCuratedPackage(), package2.toCuratedPackage(), package3.toCuratedPackage()),
        listOf(issue4)
    )

    init {
        "AnalyzerResult" should {
            "be serialized and deserialized correctly" {
                val mergedResults = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                val serializedMergedResults = yamlMapper.writeValueAsString(mergedResults)
                val deserializedMergedResults = yamlMapper.readValue<AnalyzerResult>(serializedMergedResults)

                deserializedMergedResults shouldBe mergedResults
            }
        }

        "collectIssues" should {
            "find all issues" {
                val analyzerResult = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                analyzerResult.collectIssues() shouldBe mapOf(
                    package1.id to setOf(issue1),
                    package3.id to setOf(issue2),
                    project1.id to setOf(issue3, issue4),
                    project2.id to setOf(issue4)
                )
            }

            "contain declared license issues" {
                val invalidProjectLicense = sortedSetOf("invalid project license")
                val invalidPackageLicense = sortedSetOf("invalid package license")
                val analyzerResult = AnalyzerResult(
                    projects = sortedSetOf(
                        project1.copy(
                            declaredLicenses = invalidProjectLicense,
                            declaredLicensesProcessed = DeclaredLicenseProcessor.process(invalidProjectLicense),
                            scopes = sortedSetOf()
                        )
                    ),
                    packages = sortedSetOf(
                        package1.copy(
                            declaredLicenses = invalidPackageLicense,
                            declaredLicensesProcessed = DeclaredLicenseProcessor.process(invalidPackageLicense)
                        ).toCuratedPackage()
                    )
                )

                val issues = analyzerResult.collectIssues()

                issues.getValue(project1.id).let { projectIssues ->
                    projectIssues should haveSize(1)
                    projectIssues.first().severity shouldBe Severity.WARNING
                    projectIssues.first().source shouldBe project1.id.toCoordinates()
                    projectIssues.first().message shouldBe "The declared license 'invalid project license' could not " +
                            "be mapped to a valid license or parsed as an SPDX expression."
                }

                issues.getValue(package1.id).let { packageIssues ->
                    packageIssues should haveSize(1)
                    packageIssues.first().severity shouldBe Severity.WARNING
                    packageIssues.first().source shouldBe package1.id.toCoordinates()
                    packageIssues.first().message shouldBe "The declared license 'invalid package license' could not " +
                            "be mapped to a valid license or parsed as an SPDX expression."
                }
            }
        }

        "AnalyzerResultBuilder" should {
            "merge results from all files" {
                val mergedResults = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                mergedResults.projects shouldBe sortedSetOf(project1, project2)
                mergedResults.packages shouldBe sortedSetOf(
                    package1.toCuratedPackage(), package2.toCuratedPackage(),
                    package3.toCuratedPackage()
                )
                mergedResults.issues shouldBe
                        sortedMapOf(project1.id to analyzerResult1.issues, project2.id to analyzerResult2.issues)
            }
        }
    }
}
