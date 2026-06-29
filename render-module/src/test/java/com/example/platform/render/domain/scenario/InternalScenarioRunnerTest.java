package com.example.platform.render.domain.scenario;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.editing.*;
import com.example.platform.render.domain.timeline.render.effect.*;
import com.example.platform.render.domain.timeline.render.plan.*;
import com.example.platform.render.domain.timeline.render.transition.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Internal Scenario Runner and E2E Validation Harness.
 * Covers: domain types, registry, runner, report aggregation,
 * required scenarios, safety boundary scenarios, determinism.
 */
class InternalScenarioRunnerTest {

    // ==================== Domain Types ====================

    @Test @DisplayName("Scenario id rejects null")
    void scenarioIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new InternalScenarioId(null));
    }

    @Test @DisplayName("Scenario id rejects blank")
    void scenarioIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new InternalScenarioId(""));
    }

    @Test @DisplayName("Scenario name rejects null")
    void scenarioNameRejectsNull() {
        assertThrows(NullPointerException.class, () -> new InternalScenarioName(null));
    }

    @Test @DisplayName("Scenario name rejects blank")
    void scenarioNameRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new InternalScenarioName(""));
    }

    @Test @DisplayName("Scenario category enum contains required categories")
    void scenarioCategoryContainsRequired() {
        assertNotNull(InternalScenarioCategory.TIMELINE_EDITING);
        assertNotNull(InternalScenarioCategory.VISUAL_CAPABILITY);
        assertNotNull(InternalScenarioCategory.EFFECT_PLANNING);
        assertNotNull(InternalScenarioCategory.TRANSITION_PLANNING);
        assertNotNull(InternalScenarioCategory.BASIC_RENDER_PLANNING);
        assertNotNull(InternalScenarioCategory.SAFETY_BOUNDARY);
        assertNotNull(InternalScenarioCategory.OUTPUT_PROFILE);
        assertNotNull(InternalScenarioCategory.REGRESSION);
    }

    @Test @DisplayName("Scenario result status enum contains required statuses")
    void resultStatusContainsRequired() {
        assertNotNull(InternalScenarioResultStatus.PASS);
        assertNotNull(InternalScenarioResultStatus.PASS_WITH_WARNINGS);
        assertNotNull(InternalScenarioResultStatus.FAIL);
        assertNotNull(InternalScenarioResultStatus.BLOCKED);
        assertNotNull(InternalScenarioResultStatus.UNSUPPORTED);
        assertNotNull(InternalScenarioResultStatus.NOT_RUN);
    }

    @Test @DisplayName("Scenario issue severity enum contains required severities")
    void issueSeverityContainsRequired() {
        assertNotNull(InternalScenarioIssueSeverity.INFO);
        assertNotNull(InternalScenarioIssueSeverity.WARNING);
        assertNotNull(InternalScenarioIssueSeverity.ERROR);
        assertNotNull(InternalScenarioIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Scenario issue code enum contains required codes")
    void issueCodeContainsRequired() {
        assertNotNull(InternalScenarioIssueCode.SCENARIO_DEFINITION_INVALID);
        assertNotNull(InternalScenarioIssueCode.TIMELINE_EDITING_FAILED);
        assertNotNull(InternalScenarioIssueCode.TIMELINE_VALIDATION_FAILED);
        assertNotNull(InternalScenarioIssueCode.EFFECT_PLAN_FAILED);
        assertNotNull(InternalScenarioIssueCode.EFFECT_PLAN_BLOCKED);
        assertNotNull(InternalScenarioIssueCode.TRANSITION_PLAN_FAILED);
        assertNotNull(InternalScenarioIssueCode.TRANSITION_PLAN_BLOCKED);
        assertNotNull(InternalScenarioIssueCode.BASIC_RENDER_PLAN_FAILED);
        assertNotNull(InternalScenarioIssueCode.BASIC_RENDER_PLAN_BLOCKED);
        assertNotNull(InternalScenarioIssueCode.OUTPUT_PROFILE_INVALID);
        assertNotNull(InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH);
        assertNotNull(InternalScenarioIssueCode.FORBIDDEN_EFFECT_NOT_BLOCKED);
        assertNotNull(InternalScenarioIssueCode.FORBIDDEN_TRANSITION_NOT_BLOCKED);
        assertNotNull(InternalScenarioIssueCode.RAW_FILTERGRAPH_EXPOSED);
        assertNotNull(InternalScenarioIssueCode.RAW_PROVIDER_COMMAND_EXPOSED);
        assertNotNull(InternalScenarioIssueCode.USER_RENDER_DAG_ALLOWED);
        assertNotNull(InternalScenarioIssueCode.PLUGIN_EXECUTION_NODE_ALLOWED);
        assertNotNull(InternalScenarioIssueCode.REMOTION_EXECUTION_ALLOWED);
        assertNotNull(InternalScenarioIssueCode.ARTIFACT_DAG_USED);
        assertNotNull(InternalScenarioIssueCode.FFMPEG_EXECUTION_ATTEMPTED);
        assertNotNull(InternalScenarioIssueCode.OPEN_CUE_CALL_ATTEMPTED);
        assertNotNull(InternalScenarioIssueCode.PRODUCT_RUNTIME_CALL_ATTEMPTED);
        assertNotNull(InternalScenarioIssueCode.STORAGE_RUNTIME_CALL_ATTEMPTED);
        assertNotNull(InternalScenarioIssueCode.PUBLIC_API_EXPOSED);
        assertNotNull(InternalScenarioIssueCode.PERSISTENCE_ATTEMPTED);
    }

    @Test @DisplayName("Scenario issue factory methods set correct severity")
    void issueFactoryMethods() {
        InternalScenarioIssue info = InternalScenarioIssue.info(
                InternalScenarioIssueCode.SCENARIO_DEFINITION_INVALID, "test");
        assertEquals(InternalScenarioIssueSeverity.INFO, info.severity());

        InternalScenarioIssue warning = InternalScenarioIssue.warning(
                InternalScenarioIssueCode.EXPECTED_OUTCOME_MISMATCH, "test");
        assertEquals(InternalScenarioIssueSeverity.WARNING, warning.severity());

        InternalScenarioIssue error = InternalScenarioIssue.error(
                InternalScenarioIssueCode.TIMELINE_VALIDATION_FAILED, "test");
        assertEquals(InternalScenarioIssueSeverity.ERROR, error.severity());

        InternalScenarioIssue blocking = InternalScenarioIssue.blocking(
                InternalScenarioIssueCode.EFFECT_PLAN_BLOCKED, "test");
        assertEquals(InternalScenarioIssueSeverity.BLOCKING, blocking.severity());
    }

    @Test @DisplayName("Scenario result passed() returns true for PASS and PASS_WITH_WARNINGS")
    void resultPassedMethod() {
        InternalScenarioResult passResult = new InternalScenarioResult(
                new InternalScenarioId("test"), new InternalScenarioName("test"),
                InternalScenarioCategory.TIMELINE_EDITING, InternalScenarioResultStatus.PASS,
                new InternalScenarioExpectedOutcome(InternalScenarioResultStatus.PASS, List.of(), Map.of(), Map.of()),
                InternalScenarioActualOutcome.of(InternalScenarioResultStatus.PASS, List.of(), Map.of(), List.of()),
                List.of(), Map.of());
        assertTrue(passResult.passed());

        InternalScenarioResult passWarnResult = new InternalScenarioResult(
                new InternalScenarioId("test"), new InternalScenarioName("test"),
                InternalScenarioCategory.TIMELINE_EDITING, InternalScenarioResultStatus.PASS_WITH_WARNINGS,
                new InternalScenarioExpectedOutcome(InternalScenarioResultStatus.PASS, List.of(), Map.of(), Map.of()),
                InternalScenarioActualOutcome.of(InternalScenarioResultStatus.PASS_WITH_WARNINGS, List.of(), Map.of(), List.of()),
                List.of(), Map.of());
        assertTrue(passWarnResult.passed());

        InternalScenarioResult failResult = new InternalScenarioResult(
                new InternalScenarioId("test"), new InternalScenarioName("test"),
                InternalScenarioCategory.TIMELINE_EDITING, InternalScenarioResultStatus.FAIL,
                new InternalScenarioExpectedOutcome(InternalScenarioResultStatus.PASS, List.of(), Map.of(), Map.of()),
                InternalScenarioActualOutcome.of(InternalScenarioResultStatus.FAIL, List.of(), Map.of(), List.of()),
                List.of(), Map.of());
        assertFalse(failResult.passed());
    }

    // ==================== Registry ====================

    @Test @DisplayName("Registry contains all 10 required scenario ids")
    void registryContainsRequiredIds() {
        List<InternalScenarioDefinition> all = InternalScenarioRegistry.allRequired();
        Set<String> ids = all.stream().map(d -> d.id().value()).collect(Collectors.toSet());
        assertTrue(ids.contains("scenario-001-basic-timeline-create"));
        assertTrue(ids.contains("scenario-002-caption-overlay-render-plan"));
        assertTrue(ids.contains("scenario-003-watermark-overlay-render-plan"));
        assertTrue(ids.contains("scenario-004-effect-plan-scale-crop-opacity"));
        assertTrue(ids.contains("scenario-005-transition-plan-cut-crossfade"));
        assertTrue(ids.contains("scenario-006-basic-render-plan-composition"));
        assertTrue(ids.contains("scenario-007-invalid-effect-forbidden-filtergraph"));
        assertTrue(ids.contains("scenario-008-invalid-transition-user-defined-graph"));
        assertTrue(ids.contains("scenario-009-output-profile-validation"));
        assertTrue(ids.contains("scenario-010-full-basic-planning-flow"));
        assertEquals(10, all.size());
    }

    @Test @DisplayName("Registry findById returns correct scenario")
    void registryFindById() {
        Optional<InternalScenarioDefinition> found = InternalScenarioRegistry.findById("scenario-001-basic-timeline-create");
        assertTrue(found.isPresent());
        assertEquals("Basic Timeline Create", found.get().name().value());
    }

    @Test @DisplayName("Registry findById returns empty for unknown")
    void registryFindByIdUnknown() {
        Optional<InternalScenarioDefinition> found = InternalScenarioRegistry.findById("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test @DisplayName("All required scenarios are ACTIVE")
    void allRequiredScenariosActive() {
        for (InternalScenarioDefinition def : InternalScenarioRegistry.allRequired()) {
            assertEquals(InternalScenarioStatus.ACTIVE, def.status(),
                    "Scenario " + def.id().value() + " should be ACTIVE");
        }
    }

    // ==================== Required Scenarios ====================

    @Test @DisplayName("scenario-001: basic timeline create passes")
    void scenario001BasicTimelineCreate() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-001-basic-timeline-create").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
    }

    @Test @DisplayName("scenario-002: caption overlay render plan passes")
    void scenario002CaptionOverlay() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-002-caption-overlay-render-plan").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
        // Verify caption steps were produced
        assertTrue(result.actualOutcome().actualPlanProperties().containsKey("hasCaptionSteps"));
    }

    @Test @DisplayName("scenario-003: watermark overlay render plan passes")
    void scenario003WatermarkOverlay() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-003-watermark-overlay-render-plan").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
        assertTrue(result.actualOutcome().actualPlanProperties().containsKey("hasWatermarkSteps"));
    }

    @Test @DisplayName("scenario-004: effect plan produces expected operations")
    void scenario004EffectPlan() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-004-effect-plan-scale-crop-opacity").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
        Object count = result.actualOutcome().actualPlanProperties().get("effectOperationCount");
        assertNotNull(count, "effectOperationCount should be present");
        assertTrue(((Number) count).intValue() >= 3, "Expected >= 3 effect operations");
    }

    @Test @DisplayName("scenario-005: transition plan produces expected operations")
    void scenario005TransitionPlan() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-005-transition-plan-cut-crossfade").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
        Object count = result.actualOutcome().actualPlanProperties().get("transitionOperationCount");
        assertNotNull(count, "transitionOperationCount should be present");
        assertTrue(((Number) count).intValue() >= 1, "Expected >= 1 transition operation");
    }

    @Test @DisplayName("scenario-006: basic render plan composition produces expected stages")
    void scenario006RenderPlanComposition() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-006-basic-render-plan-composition").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.PASS, result.status(), "Issues: " + result.issues());
        Object stagesCount = result.actualOutcome().actualPlanProperties().get("stagesCount");
        assertNotNull(stagesCount, "stagesCount should be present");
        assertTrue(((Number) stagesCount).intValue() >= 5, "Expected >= 5 stages");
    }

    @Test @DisplayName("scenario-007: forbidden filtergraph effect is blocked")
    void scenario007ForbiddenFiltergraph() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-007-invalid-effect-forbidden-filtergraph").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.BLOCKED, result.status(),
                "Expected BLOCKED for forbidden filtergraph, got " + result.status() + ". Issues: " + result.issues());
    }

    @Test @DisplayName("scenario-008: user-defined transition graph is blocked")
    void scenario008UserDefinedGraph() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-008-invalid-transition-user-defined-graph").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.BLOCKED, result.status(),
                "Expected BLOCKED for user-defined graph, got " + result.status() + ". Issues: " + result.issues());
    }

    @Test @DisplayName("scenario-009: unsupported output profile is blocked")
    void scenario009OutputProfile() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-009-output-profile-validation").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertEquals(InternalScenarioResultStatus.BLOCKED, result.status(),
                "Expected BLOCKED for unsupported output profile, got " + result.status() + ". Issues: " + result.issues());
    }

    @Test @DisplayName("scenario-010: full basic planning flow passes")
    void scenario010FullBasicPlanningFlow() {
        InternalScenarioDefinition def = InternalScenarioRegistry.findById("scenario-010-full-basic-planning-flow").orElseThrow();
        InternalScenarioResult result = InternalScenarioRunner.run(def);
        assertTrue(result.passed(), "Expected PASS or PASS_WITH_WARNINGS, got " + result.status() + ". Issues: " + result.issues());
        Object stagesCount = result.actualOutcome().actualPlanProperties().get("stagesCount");
        assertNotNull(stagesCount, "stagesCount should be present");
        assertTrue(((Number) stagesCount).intValue() >= 8, "Expected >= 8 stages for full flow");
    }

    // ==================== Report Aggregation ====================

    @Test @DisplayName("Scenario report aggregates deterministic counts")
    void reportAggregatesCounts() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        assertEquals(10, report.totalScenarios());
        assertEquals(report.totalScenarios(),
                report.passed() + report.passedWithWarnings() + report.failed()
                + report.blocked() + report.unsupported() + report.notRun());
    }

    @Test @DisplayName("Scenario results are deterministic across double-run")
    void resultsDeterministic() {
        InternalScenarioReport report1 = InternalScenarioRunner.runAllRequired();
        InternalScenarioReport report2 = InternalScenarioRunner.runAllRequired();

        assertEquals(report1.totalScenarios(), report2.totalScenarios());
        assertEquals(report1.passed(), report2.passed());
        assertEquals(report1.passedWithWarnings(), report2.passedWithWarnings());
        assertEquals(report1.failed(), report2.failed());
        assertEquals(report1.blocked(), report2.blocked());
        assertEquals(report1.unsupported(), report2.unsupported());
        assertEquals(report1.notRun(), report2.notRun());

        for (int i = 0; i < report1.results().size(); i++) {
            InternalScenarioResult r1 = report1.results().get(i);
            InternalScenarioResult r2 = report2.results().get(i);
            assertEquals(r1.scenarioId().value(), r2.scenarioId().value());
            assertEquals(r1.status(), r2.status(),
                    "Scenario " + r1.scenarioId().value() + " had different status across runs");
        }
    }

    @Test @DisplayName("Scenario report results are ordered by scenario id")
    void reportResultsOrdered() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (int i = 1; i < report.results().size(); i++) {
            String prev = report.results().get(i - 1).scenarioId().value();
            String curr = report.results().get(i).scenarioId().value();
            assertTrue(prev.compareTo(curr) < 0,
                    "Results should be ordered by id: " + prev + " should be before " + curr);
        }
    }

    @Test @DisplayName("Scenario report has valid report id")
    void reportHasValidId() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        assertNotNull(report.reportId());
        assertFalse(report.reportId().isBlank());
    }

    // ==================== Safety Boundary Tests ====================

    @Test @DisplayName("Runner does not expose filter_complex in results")
    void noFilterComplexExposed() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("filter_complex"),
                        "Issue message should not contain filter_complex: " + issue.message());
            }
        }
    }

    @Test @DisplayName("Runner does not expose raw shell commands")
    void noRawShellCommands() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("Runtime.getRuntime"),
                        "Issue should not reference Runtime.getRuntime");
                assertFalse(issue.message().contains("ProcessBuilder"),
                        "Issue should not reference ProcessBuilder");
            }
        }
    }

    @Test @DisplayName("Runner does not reference OpenCue")
    void noOpenCueReferences() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("cuebot"),
                        "Issue should not reference cuebot");
                assertFalse(issue.message().contains("rqd"),
                        "Issue should not reference rqd");
            }
        }
    }

    @Test @DisplayName("Runner does not reference StorageRuntime internals")
    void noStorageRuntimeReferences() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("bucket"),
                        "Issue should not reference bucket");
                assertFalse(issue.message().contains("objectKey"),
                        "Issue should not reference objectKey");
                assertFalse(issue.message().contains("signedUrl"),
                        "Issue should not reference signedUrl");
            }
        }
    }

    @Test @DisplayName("Runner does not reference ProductRuntime internals")
    void noProductRuntimeReferences() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("productRuntime"),
                        "Issue should not reference productRuntime");
            }
        }
    }

    @Test @DisplayName("Runner does not reference Remotion execution")
    void noRemotionExecution() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("npx remotion"),
                        "Issue should not reference npx remotion");
                assertFalse(issue.message().contains("remotion render"),
                        "Issue should not reference remotion render");
            }
        }
    }

    @Test @DisplayName("Runner does not reference global optimization")
    void noGlobalOptimization() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            for (InternalScenarioIssue issue : result.issues()) {
                assertFalse(issue.message().contains("global optimization"),
                        "Issue should not reference global optimization");
                assertFalse(issue.message().contains("NP-hard"),
                        "Issue should not reference NP-hard");
            }
        }
    }

    // ==================== Result Safety Checks ====================

    @Test @DisplayName("No scenario result contains provider internals in safeMetadata")
    void noProviderInternalsInMetadata() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            assertFalse(result.safeMetadata().containsKey("bucket"),
                    "Safe metadata should not contain bucket");
            assertFalse(result.safeMetadata().containsKey("objectKey"),
                    "Safe metadata should not contain objectKey");
            assertFalse(result.safeMetadata().containsKey("signedUrl"),
                    "Safe metadata should not contain signedUrl");
            assertFalse(result.safeMetadata().containsKey("workerHost"),
                    "Safe metadata should not contain workerHost");
        }
    }

    @Test @DisplayName("No scenario result contains OpenCue job/layer/frame ids")
    void noOpenCueIds() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            assertFalse(result.safeMetadata().containsKey("openCueJobId"),
                    "Safe metadata should not contain openCueJobId");
            assertFalse(result.safeMetadata().containsKey("openCueLayerId"),
                    "Safe metadata should not contain openCueLayerId");
            assertFalse(result.safeMetadata().containsKey("openCueFrameId"),
                    "Safe metadata should not contain openCueFrameId");
        }
    }

    @Test @DisplayName("No scenario result uses Artifact DAG")
    void noArtifactDagUsed() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            assertFalse(result.safeMetadata().containsKey("artifactGraphId"),
                    "Safe metadata should not contain artifactGraphId");
            assertFalse(result.safeMetadata().containsKey("capabilityGraphId"),
                    "Safe metadata should not contain capabilityGraphId");
        }
    }

    @Test @DisplayName("No scenario result references Remotion")
    void noRemotionReferences() {
        InternalScenarioReport report = InternalScenarioRunner.runAllRequired();
        for (InternalScenarioResult result : report.results()) {
            assertFalse(result.safeMetadata().containsKey("remotionInputProps"),
                    "Safe metadata should not contain remotionInputProps");
        }
    }
}
