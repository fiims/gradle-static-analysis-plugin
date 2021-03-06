package com.novoda.staticanalysis

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor

import static com.google.common.truth.Truth.assertThat
import static org.junit.Assert.fail
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify

class DefaultViolationsEvaluatorTest {

    private static final String TOOL_NAME = 'SomeTool'

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    private PenaltyExtension penalty
    private Violations violations
    private File reportFile
    private Logger logger = mock(Logger)

    private DefaultViolationsEvaluator evaluator

    @Before
    void setUp() {
        penalty = new PenaltyExtension()
        penalty.maxErrors = 1
        penalty.maxWarnings = 1
        violations = new Violations(TOOL_NAME)
        reportFile = temporaryFolder.newFile('report.xml')
        evaluator = new DefaultViolationsEvaluator(ReportUrlRenderer.DEFAULT, logger, penalty)
    }

    @Test
    void shouldLogViolationsNumberWhenBelowThreshold() {
        violations.addViolations(1, 0, reportFile)

        evaluator.evaluate(allViolations)

        assertThat(warningLog).contains("$TOOL_NAME violations found (1 errors, 0 warnings).")
    }

    @Test
    void shouldNotLogViolationsNumberWhenNoViolations() {
        violations.addViolations(0, 0, reportFile)

        evaluator.evaluate(allViolations)

        assertThat(warningLog).doesNotContain("$TOOL_NAME violations found")
    }

    @Test
    void shouldThrowExceptionWhenViolationsNumberWhenAboveThreshold() {
        violations.addViolations(1, 2, reportFile)

        try {
            evaluator.evaluate(allViolations)
            fail('Exception expected but not thrown')
        } catch (GradleException e) {
            assertThat(e.message).contains('Violations limit exceeded by 0 errors, 1 warnings.')
        }
    }

    private Set<Violations> getAllViolations() {
        [violations] as Set<Violations>
    }

    private String getWarningLog() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String)
        verify(logger).warn(captor.capture())
        captor.getValue()
    }
}
