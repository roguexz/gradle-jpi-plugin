package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import spock.lang.Unroll

class LicenseTaskSpec extends IntegrationSpec {

    @Unroll
    def 'compute license information - #buildFile'(String buildFile, String expectedLicensesFile) {
        given:
        File projectFolder = projectDir.newFolder('bar')
        new File(projectFolder, 'build.gradle') << getClass().getResource(buildFile).text

        when:
        def result = gradleRunner()
                .withProjectDir(projectFolder)
                .withArguments('generateLicenseInfo', "-PembeddedIvyUrl=${TestSupport.EMBEDDED_IVY_URL}")
                .build()

        then:
        result.task(':generateLicenseInfo').outcome == TaskOutcome.SUCCESS
        File licensesFile = new File(projectFolder, 'build/licenses/licenses.xml')
        licensesFile.exists()
        compareXml(licensesFile.text, getClass().getResource(expectedLicensesFile).text)

        where:
        buildFile                      | expectedLicensesFile
        'licenseInfo.gradle'           | 'licenses.xml'
        'licenseInfoWithKotlin.gradle' | 'licensesWithKotlin.xml'
    }

    private static boolean compareXml(String actual, String expected) {
        !DiffBuilder.compare(Input.fromString(actual))
                .withTest(Input.fromString(expected))
                .checkForSimilar()
                .ignoreWhitespace()
                .build()
                .hasDifferences()
    }
}
