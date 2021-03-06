package com.novoda.staticanalysis.internal.detekt

import com.novoda.test.Fixtures
import com.novoda.test.TestProject
import com.novoda.test.TestProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.novoda.test.LogsSubject.assertThat

@RunWith(Parameterized.class)
class DetektIntegrationTest {

    private static final String DETEKT_NOT_APPLIED = 'The Detekt plugin is configured but not applied. Please apply the plugin in your build script.'
    private static final String OUTPUT_NOT_DEFINED = 'Output not defined! To analyze the results, `output` needs to be defined in Detekt profile.'

    @Parameterized.Parameters(name = "{0} with Detekt: {1}")
    static Iterable rules() {
        return [
                [TestProjectRule.forKotlinProject(), "1.0.0.RC6-2"],
                [TestProjectRule.forAndroidKotlinProject(), "1.0.0.RC6-2"],
                [TestProjectRule.forKotlinProject(), "1.0.0.RC8"],
                [TestProjectRule.forAndroidKotlinProject(), "1.0.0.RC8"],
        ]*.toArray()
    }

    @Rule
    public final TestProjectRule projectRule
    private final String detektVersion

    DetektIntegrationTest(TestProjectRule projectRule, String detektVersion) {
        this.projectRule = projectRule
        this.detektVersion = detektVersion
    }

    @Test
    void shouldFailBuildOnConfigurationWhenNoOutputNotDefined() {
        def emptyConfiguration = detektWith(detektVersion, "")

        def result = createProjectWithZeroThreshold(Fixtures.Detekt.SOURCES_WITH_WARNINGS)
                .withToolsConfig(emptyConfiguration)
                .buildAndFail('check')

        assertThat(result.logs).contains(OUTPUT_NOT_DEFINED)
    }

    @Test
    void shouldFailBuildOnConfigurationWhenDetektConfiguredButNotApplied() {
        def result = projectRule.newProject()
                .withToolsConfig(detektConfiguration(Fixtures.Detekt.SOURCES_WITH_ERRORS, detektVersion))
                .buildAndFail('check')

        assertThat(result.logs).contains(DETEKT_NOT_APPLIED)
    }

    @Test
    void shouldFailBuildWhenDetektWarningsOverTheThreshold() {
        def result = createProjectWithZeroThreshold(Fixtures.Detekt.SOURCES_WITH_WARNINGS)
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(0, 1)
        assertThat(result.logs).containsDetektViolations(0, 1,
                result.buildFileUrl('reports/detekt-report.html'))
    }

    @Test
    void shouldFailBuildWhenDetektErrorsOverTheThreshold() {
        def result = createProjectWithZeroThreshold(Fixtures.Detekt.SOURCES_WITH_ERRORS)
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(1, 0)
        assertThat(result.logs).containsDetektViolations(1, 0,
                result.buildFileUrl('reports/detekt-report.html'))
    }

    @Test
    void shouldNotFailWhenDetektIsNotConfigured() throws Exception {
        def result = createProjectWithoutDetekt()
                .build('check')

        assertThat(result.logs).doesNotContainDetektViolations()
    }

    @Test
    void shouldNotFailWhenWarningsAreWithinThreshold() throws Exception {
        def result = createProjectWith(Fixtures.Detekt.SOURCES_WITH_WARNINGS, 1, 0)
                .build('check')

        assertThat(result.logs).containsDetektViolations(0, 1,
                result.buildFileUrl('reports/detekt-report.html'))
    }

    @Test
    void shouldNotFailWhenErrorsAreWithinThreshold() throws Exception {
        def result = createProjectWith(Fixtures.Detekt.SOURCES_WITH_ERRORS, 0, 1)
                .build('check')

        assertThat(result.logs).containsDetektViolations(1, 0,
                result.buildFileUrl('reports/detekt-report.html'))
    }

    @Test
    void shouldNotFailBuildWhenNoDetektWarningsOrErrorsEncounteredAndNoThresholdTrespassed() {
        def testProject = projectRule.newProject()
                .withPlugin("io.gitlab.arturbosch.detekt", detektVersion)
                .withPenalty('''{
                    maxWarnings = 0
                    maxErrors = 0
                }''')
                .withToolsConfig(detektConfigurationWithoutInput(detektVersion))

        TestProject.Result result = testProject
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).doesNotContainDetektViolations()
    }

    private TestProject createProjectWithZeroThreshold(File sources) {
        createProjectWith(sources)
    }

    private TestProject createProjectWith(File sources, int maxWarnings = 0, int maxErrors = 0) {
        projectRule.newProject()
                .withPlugin("io.gitlab.arturbosch.detekt", detektVersion)
                .withSourceSet('main', sources)
                .withPenalty("""{
                    maxWarnings = ${maxWarnings}
                    maxErrors = ${maxErrors}
                }""")
                .withToolsConfig(detektConfiguration(sources, detektVersion))
    }

    private TestProject createProjectWithoutDetekt() {
        projectRule.newProject()
                .withPlugin("io.gitlab.arturbosch.detekt", detektVersion)
                .withSourceSet('main', Fixtures.Detekt.SOURCES_WITH_WARNINGS)
                .withPenalty('''{
                    maxWarnings = 0
                    maxErrors = 0
                }''')
    }

    private static String detektConfiguration(File input, String detektVersion) {
        detektWith(detektVersion, """
            config = '${Fixtures.Detekt.RULES}' 
            output = "\$buildDir/reports"
            // The input just needs to be configured for the tests. 
            // Probably detekt doesn't pick up the changed source sets. 
            // In a example project it was not needed.
            input = "${input}"
        """)
    }

    private static String detektConfigurationWithoutInput(String detektVersion) {
        detektWith(detektVersion, """
            config = '${Fixtures.Detekt.RULES}' 
            output = "\$buildDir/reports"
        """)
    }

    private static String detektWith(String detektVersion, String mainProfile) {
        """
        detekt {      
            version '${detektVersion}'
            
            profile('main') { 
                ${mainProfile.stripIndent()}
            }
        }
        """
    }
}
