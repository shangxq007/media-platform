package com.example.platform.render.app.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class H8OperationInvocationBoundaryGuardTest {

    private static final Path ROOT = repositoryRoot(Path.of(System.getProperty("user.dir")));
    private static final Path GUARD =
            ROOT.resolve("scripts/guards/h8-operation-invocation-boundary-guard.py");
    private static final List<String> EXACT_CENSUS = List.of(
            "OPERATION_REQUEST_AUTHORITY_COUNT=1",
            "NEW_OPERATION_REQUEST_PEER_TYPE_COUNT=0",
            "OPERATION_INVOCATION_PORT_AUTHORITY_COUNT=1",
            "OPERATION_INVOCATION_PORT_IMPLEMENTATION_COUNT=1",
            "WORKFLOW_PEER_INVOCATION_PORT_COUNT=0",
            "WORKFLOW_PEER_OPERATION_RESULT_AUTHORITY_COUNT=0",
            "LEGACY_WORKFLOW_INVOCATION_PORT_DEFINITION_COUNT=0",
            "LEGACY_WORKFLOW_OPERATION_RESULT_DEFINITION_COUNT=0",
            "WORKFLOW_OPERATION_PLAN_IMPORT_COUNT=0",
            "WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT=0",
            "WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT=0",
            "WORKFLOW_GENERIC_TIMELINE_PATCH_USAGE_COUNT=0",
            "WORKFLOW_JOOQ_IMPORT_COUNT=0",
            "WORKFLOW_CANONICAL_MEDIA_WRITER_COUNT=0",
            "WORKFLOW_OPERATION_RESULT_REAUTHORING_COUNT=0",
            "FULLY_QUALIFIED_CROSS_PACKAGE_ESCAPE_COUNT=0",
            "WORKFLOW_TO_OPERATION_UNEXPOSED_DEPENDENCY_COUNT=0",
            "WORKFLOW_OPERATION_INVOCATION_ALLOWED_DEPENDENCY_COUNT=1",
            "WORKFLOW_BROAD_OPERATION_ALLOWED_DEPENDENCY_COUNT=0",
            "BROAD_OPERATION_PACKAGE_EXPOSURE_COUNT=0",
            "FORBIDDEN_INVOCATION_PUBLIC_SIGNATURE_TYPE_COUNT=0",
            "REQUEST_CONTROLLED_ACTOR_AUTHORITY_COUNT=0",
            "REQUEST_CONTROLLED_ADMIN_AUTHORITY_COUNT=0",
            "DUPLICATE_TENANT_AUTHORITY_COUNT=0",
            "WORKFLOW_PROVENANCE_AS_CANONICAL_AUTHORITY_COUNT=0",
            "H8_PROVIDER_BINDING_FIELD_COUNT=0",
            "H8_WORKER_BINDING_FIELD_COUNT=0",
            "H8_DEVICE_BINDING_FIELD_COUNT=0",
            "H8_STORAGE_IMPLEMENTATION_IMPORT_COUNT=0",
            "MUTABLE_LATEST_FALLBACK_COUNT=0",
            "REFLECTION_OPERATION_EXECUTION_FALLBACK_COUNT=0",
            "GENERIC_OPERATION_PLANNER_FALLBACK_COUNT=0",
            "EXECUTABLE_OPERATION_DEFINITION_COUNT=1",
            "DUPLICATE_OPERATION_INVOCATION_RESULT_AUTHORITY_COUNT=0",
            "DUPLICATE_OPERATION_INVOCATION_FAILURE_AUTHORITY_COUNT=0",
            "ALTERNATE_PEER_INVOCATION_RESULT_AUTHORITY_ESCAPE_COUNT=0",
            "ALTERNATE_PEER_INVOCATION_FAILURE_AUTHORITY_ESCAPE_COUNT=0",
            "RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT=0",
            "MISSING_EXACT_BASE_CONTENT_HASH_FAILS_CLOSED_MISSING_COUNT=0",
            "H8_PUBLIC_HTTP_ROUTE_AUTHORITY_COUNT=0",
            "H8_SCHEMA_MUTATION_AUTHORITY_COUNT=0",
            "H8_JOOQ_GENERATED_MUTATION_AUTHORITY_COUNT=0",
            "CAMELCASE_SCHEMA_MARKER_ESCAPE_COUNT=0",
            "UNSUPPORTED_OPERATION_FAIL_CLOSED_MISSING_COUNT=0",
            "OPERATION_INVOCATION_PIPELINE_ORDER_VIOLATION_COUNT=0",
            "NULL_EXPECTED_PLAN_DIGEST_BYPASS_COUNT=0",
            "H8_NEW_TIMELINE_WRITER_COUNT=0",
            "H8_NEW_HEAD_AUTHORITY_COUNT=0",
            "H8_NEW_OPERATION_PLAN_AUTHORITY_COUNT=0",
            "CANONICAL_TIMELINE_HEAD_AUTHORITY_COUNT=1",
            "CANONICAL_TIMELINE_MUTATION_WRITER_AUTHORITY_COUNT=1",
            "H8_CHANGED_PATH_SCOPE_VIOLATION_COUNT=0",
            "H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT=0",
            "UNCLASSIFIED=0");
    private static final List<String> REQUIRED_RECOVERY_CONTROLS = List.of(
            "second_invocation_result_authority",
            "second_invocation_failure_authority",
            "h8_direct_timeline_writer_access",
            "h8_timeline_head_mutation_authority",
            "h8_operation_plan_shadow",
            "h8_public_http_route",
            "h8_schema_mutation_authority",
            "h8_generated_jooq_mutation_authority",
            "unbounded_registered_definition_dispatch",
            "second_executable_operation_definition",
            "raw_string_failure_inference",
            "missing_base_content_hash_fails_open",
            "missing_base_hash_latest_fallback",
            "called_helper_direct_timeline_writer",
            "called_helper_timeline_ref_head_mutation",
            "called_helper_operation_planner_plan_result_authority",
            "called_helper_registered_definition_dispatch",
            "called_helper_raw_message_inference",
            "called_helper_missing_hash_latest_fallback",
            "workflow_fully_qualified_operation_plan",
            "workflow_fully_qualified_operation_planner",
            "workflow_fully_qualified_jooq",
            "canonical_service_public_controller_injection",
            "timeline_service_public_controller_injection",
            "generic_h8_schema_mutation",
            "generic_h8_generated_jooq_type",
            "new_unclassified_h8_runtime_source",
            "fqcn_workflow_to_operation_internal",
            "fqcn_workflow_to_timeline_writer",
            "fqcn_called_helper_all_mechanics",
            "fqcn_called_helper_known_name_spoof",
            "imported_known_name_spoof_helper",
            "imported_known_name_nested_InvocationReceipt",
            "imported_known_name_helper_all_forbidden_mechanics",
            "alternate_peer_InvocationReceipt",
            "alternate_peer_InvocationOutcome",
            "alternate_peer_InvocationResponse",
            "alternate_peer_InvocationResult",
            "alternate_peer_InvocationReply",
            "alternate_peer_InvocationProblem",
            "alternate_peer_InvocationError",
            "alternate_peer_InvocationFailure",
            "alternate_peer_InvocationFault",
            "structural_peer_invocation_result_record",
            "structural_peer_invocation_failure_record",
            "pascalcase_schema_markers",
            "lowercamel_schema_markers",
            "marker_independent_central_schema_path_mutation",
            "nullable_expected_plan_digest_bypass",
            "internal_null_expected_plan_digest",
            "wrong_digest_absorbed_by_assignment",
            "prepare_internal_authorization_after_hydration",
            "public_preview_prepare_before_authorization",
            "public_apply_prepare_before_authorization",
            "internal_findById_before_authorization",
            "authorization_helper_contains_hydration",
            "dead_authorization_anchor_before_prepare",
            "duplicate_authorization_after_prepare",
            "alternate_peer_InvocationAcknowledgement",
            "alternate_peer_InvocationDenial",
            "imported_known_helper_nested_InvocationAcknowledgement",
            "imported_known_helper_nested_InvocationDenial",
            "authorization_denial_return_bypass",
            "authorization_denial_swallowed_catch",
            "digest_throw_swallowed_catch",
            "dead_digest_check",
            "unicode_fqcn_and_invocation_authority_escape",
            "alternate_registry_dispatch",
            "throwable_toString_failure_inference",
            "findNewest_mutable_latest_fallback",
            "invalid_unicode_escape_fails_closed",
            "workflow_operation_request_structural_peer",
            "compound_request_actor_identifier",
            "base_hash_null_and_blank_bug",
            "id_version_and_bug",
            "map_definition_dispatch",
            "constant_true_authorization_binding",
            "subtype_exception_toString",
            "descending_sort_findFirst_latest",
            "v6_canonical_actor_authority_field",
            "v6_aliased_map_definition_dispatch",
            "v6_security_decision_reassignment",
            "v6_symmetric_exception_string_comparison",
            "v6_split_descending_sort_get_zero",
            "v6_null_and_blank_base_hash",
            "v6_definition_id_and_version",
            "v6_constant_true_authorization",
            "workflow_call_receipt_structural_result",
            "workflow_call_denial_structural_failure",
            "changed_parameter_digest",
            "changed_rbac_authorization_decision_port",
            "changed_default_timeline_revision_persistence",
            "changed_timeline_revision_semantic_context_json_codec",
            "new_primary_permit_all_decision_port",
            "changed_unrelated_production_source",
            "workflow_unrelated_operation_request_dto",
            "workflow_unrelated_process_receipt_dto",
            "workflow_unrelated_process_denial_dto",
            "unrelated_unchanged_source_absent_from_changed_paths",
            "governed_runtime_source_hash_all_files",
            "guard_rule_removed_runtime_validation");
    private static final List<String> REQUIRED_SCOPE_LIFECYCLE_CONTROLS = List.of(
            "current_frontend_descendant_head",
            "historical_h8_delta_exact_authorized_22_paths",
            "missing_and_invalid_accepted_checkpoint_fail_closed",
            "checkout_not_descended_from_accepted_fails_closed",
            "legitimate_descendant_paths_excluded_from_historical_scope",
            "unauthorized_h8_candidate_scope_mutation");

    @Test
    void guardReportsTheExactCensusAndPasses() throws Exception {
        assertTrue(Files.isRegularFile(GUARD), "H8 guard must exist");
        Result result = run(List.of("python3", GUARD.toString(), "--root", ROOT.toString()));

        assertExactCensus(result);
    }

    @Test
    void mutationMatrixExercisesAtLeastTwoHundredSixControls() throws Exception {
        Result result = run(List.of(
                "python3", GUARD.toString(), "--root", ROOT.toString(), "--self-test"));

        assertEquals(0, result.exitCode(), result.output());
        var total = Pattern.compile("(?m)^H8_MUTATION_MATRIX_TOTAL=(\\d+)$")
                .matcher(result.output());
        assertTrue(total.find(), result.output());
        assertTrue(Integer.parseInt(total.group(1)) >= 206, result.output());
        REQUIRED_RECOVERY_CONTROLS.forEach(control -> assertTrue(result.output().lines()
                .anyMatch(line -> line.startsWith("H8_MUTATION " + control + "=PASS")),
                "missing recovery negative control: " + control + "\n" + result.output()));
        REQUIRED_SCOPE_LIFECYCLE_CONTROLS.forEach(control -> assertTrue(
                result.output().lines()
                        .anyMatch(line -> line.startsWith("H8_MUTATION " + control + "=PASS")),
                "missing scope lifecycle control: " + control + "\n" + result.output()));
        assertTrue(result.output().lines()
                .anyMatch("OLD_H8_MUTATION_REGRESSION_COUNT=0"::equals), result.output());
        assertTrue(result.output().lines()
                .anyMatch("NEW_H8_HOSTILE_MUTATION_FAILURES=0"::equals), result.output());
        assertTrue(result.output().lines()
                .anyMatch("H8_MUTATION_MATRIX_FAILURES=0"::equals), result.output());
    }

    @Test
    void exactCensusRejectsTemporaryGuardRuleRemoval() throws Exception {
        String rule = "    \"RAW_STRING_INVOCATION_FAILURE_INFERENCE_COUNT\",\n";
        String original = Files.readString(GUARD);
        assertTrue(original.contains(rule), "guard rule mutation anchor must exist");
        Path temporaryDirectory = Files.createTempDirectory("h8-guard-rule-removal-");
        Path mutatedGuard = temporaryDirectory.resolve(GUARD.getFileName());
        try {
            Files.writeString(mutatedGuard, original.replace(rule, ""));
            Result result = run(List.of(
                    "python3", mutatedGuard.toString(), "--root", ROOT.toString()));

            assertNotEquals(0, result.exitCode(), result.output());
            assertThrows(AssertionError.class, () -> assertExactCensus(result),
                    "removing a guard rule must make the JUnit exact-census assertion fail");
        } finally {
            Files.deleteIfExists(mutatedGuard);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    @Test
    void missingRepositoryRootFailsClosedWithUnclassified() throws Exception {
        Path missingRoot = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("h8-missing-repository-root-" + ProcessHandle.current().pid()
                        + "-" + System.nanoTime());
        assertFalse(Files.exists(missingRoot), "negative-control root must remain absent");

        Result result = run(List.of(
                "python3", GUARD.toString(), "--root", missingRoot.toString()));

        assertNotEquals(0, result.exitCode(), result.output());
        var unclassified = Pattern.compile("(?m)^UNCLASSIFIED=(\\d+)$")
                .matcher(result.output());
        assertTrue(unclassified.find(), result.output());
        assertTrue(Integer.parseInt(unclassified.group(1)) > 0, result.output());
        var hashMismatch = Pattern.compile(
                "(?m)^H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT=(\\d+)$")
                .matcher(result.output());
        assertTrue(hashMismatch.find(), result.output());
        assertTrue(Integer.parseInt(hashMismatch.group(1)) > 0, result.output());
        assertFalse(result.output().contains("H8_OPERATION_INVOCATION_BOUNDARY_GUARD=PASS"),
                result.output());
    }

    @Test
    void missingAcceptedCanonicalCheckpointFailsClosedWithUnclassified() throws Exception {
        String acceptedCheckpoint = "H8_ACCEPTED_CANONICAL_SHA = "
                + "\"16e0022e91e384fc05dfd8497c29640c8deec195\"";
        String original = Files.readString(GUARD);
        assertTrue(original.contains(acceptedCheckpoint),
                "immutable accepted-checkpoint anchor must exist");
        Path temporaryDirectory = Files.createTempDirectory("h8-missing-accepted-checkpoint-");
        Path mutatedGuard = temporaryDirectory.resolve(GUARD.getFileName());
        try {
            Files.writeString(mutatedGuard, original.replace(
                    acceptedCheckpoint,
                    "H8_ACCEPTED_CANONICAL_SHA = \"0000000000000000000000000000000000000000\""));
            Result result = run(List.of(
                    "python3", mutatedGuard.toString(), "--root", ROOT.toString()));

            assertNotEquals(0, result.exitCode(), result.output());
            var unclassified = Pattern.compile("(?m)^UNCLASSIFIED=(\\d+)$")
                    .matcher(result.output());
            assertTrue(unclassified.find(), result.output());
            assertTrue(Integer.parseInt(unclassified.group(1)) > 0, result.output());
            assertTrue(result.output().contains(
                    "H8_SCOPE_ATTESTATION_CURRENT_HEAD_DESCENDANT=FAIL"), result.output());
            assertTrue(result.output().contains(
                    "H8 accepted canonical commit does not exist"), result.output());
            assertFalse(result.output().contains("H8_OPERATION_INVOCATION_BOUNDARY_GUARD=PASS"),
                    result.output());
        } finally {
            Files.deleteIfExists(mutatedGuard);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    private static Result run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(ROOT.toFile())
                .redirectErrorStream(true);
        builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
        Process process = builder.start();
        var outputBytes = new ByteArrayOutputStream();
        var readerFailure = new AtomicReference<IOException>();
        Thread outputReader = new Thread(() -> {
            try {
                process.getInputStream().transferTo(outputBytes);
            } catch (IOException failure) {
                readerFailure.set(failure);
            }
        }, "h8-guard-output-reader");
        outputReader.start();
        boolean completed = process.waitFor(Duration.ofSeconds(600).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        outputReader.join(Duration.ofSeconds(30).toMillis());
        assertTrue(completed, "guard process timed out");
        assertFalse(outputReader.isAlive(), "guard output reader timed out");
        if (readerFailure.get() != null) {
            throw readerFailure.get();
        }
        String output = outputBytes.toString();
        return new Result(process.exitValue(), output);
    }

    private static void assertExactCensus(Result result) {
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().lines().anyMatch(
                "H8_SCOPE_ATTESTATION_BASE_SHA=b82b0dadfbee56e0436c7623e8ebc18971dc953a"
                        ::equals),
                result.output());
        assertTrue(result.output().lines().anyMatch(
                "H8_SCOPE_ATTESTATION_ACCEPTED_SHA=16e0022e91e384fc05dfd8497c29640c8deec195"
                        ::equals),
                result.output());
        assertTrue(result.output().lines().anyMatch(
                "H8_SCOPE_ATTESTATION_CURRENT_HEAD_DESCENDANT=PASS"::equals), result.output());
        assertTrue(result.output().lines().anyMatch(
                "H8_HISTORICAL_CHANGED_PATH_COUNT=22"::equals), result.output());
        EXACT_CENSUS.forEach(line -> assertTrue(result.output().lines()
                .anyMatch(line::equals), "missing exact census: " + line + "\n" + result.output()));
        assertTrue(result.output().lines()
                .anyMatch("H8_OPERATION_INVOCATION_BOUNDARY_GUARD=PASS"::equals), result.output());
    }

    private static Path repositoryRoot(Path start) {
        for (Path current = start.toAbsolutePath().normalize(); current != null;
             current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
        }
        throw new IllegalStateException("repository root not found from " + start);
    }

    private record Result(int exitCode, String output) {}
}
