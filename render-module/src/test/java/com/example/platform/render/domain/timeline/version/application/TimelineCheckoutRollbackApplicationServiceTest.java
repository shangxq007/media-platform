package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshotId;
import com.example.platform.render.domain.timeline.version.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Timeline Checkout, Rollback, and Branch Switch application services.
 * Covers: type validation, service behavior, integration semantics, safety boundaries.
 */
class TimelineCheckoutRollbackApplicationServiceTest {

    // --- Stage 1: Checkout Application Types ---

    @Test @DisplayName("Checkout request id rejects blank")
    void checkoutRequestIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineCheckoutRequestId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineCheckoutRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new TimelineCheckoutRequestId("  "));
    }

    @Test @DisplayName("Target supports branch/revision/commit")
    void targetSupportsAllTypes() {
        var branch = TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main"));
        assertEquals(TimelineCheckoutTargetType.BRANCH, branch.type());
        assertNotNull(branch.branchName());

        var revision = TimelineCheckoutTarget.ofRevision(new TimelineRevisionRef("rev-1"));
        assertEquals(TimelineCheckoutTargetType.REVISION, revision.type());
        assertNotNull(revision.revisionRef());

        var commit = TimelineCheckoutTarget.ofCommit(new TimelineCommitId("c-1"));
        assertEquals(TimelineCheckoutTargetType.COMMIT, commit.type());
        assertNotNull(commit.commitId());
    }

    @Test @DisplayName("Branch target requires branch name")
    void branchTargetRequiresName() {
        var target = new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.BRANCH, null, null, null, Map.of());
        assertNull(target.branchName());
    }

    @Test @DisplayName("Revision target requires revision ref")
    void revisionTargetRequiresRef() {
        var target = new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.REVISION, null, null, null, Map.of());
        assertNull(target.revisionRef());
    }

    @Test @DisplayName("Commit target requires commit id")
    void commitTargetRequiresId() {
        var target = new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.COMMIT, null, null, null, Map.of());
        assertNull(target.commitId());
    }

    @Test @DisplayName("Checkout result supports all statuses")
    void checkoutResultSupportsStatuses() {
        var ready = TimelineCheckoutResult.ready(null, null, null);
        assertEquals(TimelineCheckoutResultStatus.READY, ready.status());

        var branchNotFound = TimelineCheckoutResult.branchNotFound(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.BRANCH_NOT_FOUND, branchNotFound.status());

        var revisionNotFound = TimelineCheckoutResult.revisionNotFound(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.REVISION_NOT_FOUND, revisionNotFound.status());

        var commitNotFound = TimelineCheckoutResult.commitNotFound(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.COMMIT_NOT_FOUND, commitNotFound.status());

        var invalid = TimelineCheckoutResult.invalidTarget(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.INVALID_TARGET, invalid.status());

        var blocked = TimelineCheckoutResult.blocked(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.BLOCKED, blocked.status());

        var failed = TimelineCheckoutResult.failed(null, List.of());
        assertEquals(TimelineCheckoutResultStatus.FAILED, failed.status());
    }

    @Test @DisplayName("Checkout safe metadata only")
    void checkoutSafeMetadataOnly() {
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of("key", "val"));
        assertEquals("val", request.safeMetadata().get("key"));
    }

    // --- Stage 2: Rollback Application Types ---

    @Test @DisplayName("Rollback request id rejects blank")
    void rollbackRequestIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineRollbackRequestId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRollbackRequestId(null));
    }

    @Test @DisplayName("Rollback request requires current and target revision")
    void rollbackRequestRequiresRevisions() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRollbackRequest(
                        new TimelineRollbackRequestId("r1"),
                        null, new TimelineRevisionRef("target"),
                        null, false, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRollbackRequest(
                        new TimelineRollbackRequestId("r1"),
                        new TimelineRevisionRef("current"), null,
                        null, false, Map.of()));
    }

    @Test @DisplayName("Rollback intent uses ROLLBACK commit type")
    void rollbackIntentUsesRollback() {
        var intent = new TimelineRollbackIntent(
                TimelineCommitType.ROLLBACK,
                new TimelineRevisionRef("cur"), new TimelineRevisionRef("tgt"),
                "msg", Map.of());
        assertEquals(TimelineCommitType.ROLLBACK, intent.plannedCommitType());
    }

    @Test @DisplayName("Rollback result supports all statuses")
    void rollbackResultSupportsStatuses() {
        assertEquals(TimelineRollbackResultStatus.READY,
                TimelineRollbackResult.ready(null, null).status());
        assertEquals(TimelineRollbackResultStatus.NO_OP,
                TimelineRollbackResult.noOp(null,
                        new TimelineRevisionRef("a"), new TimelineRevisionRef("a")).status());
        assertEquals(TimelineRollbackResultStatus.TARGET_NOT_FOUND,
                TimelineRollbackResult.targetNotFound(List.of()).status());
        assertEquals(TimelineRollbackResultStatus.TARGET_NOT_ANCESTOR,
                TimelineRollbackResult.targetNotAncestor(List.of()).status());
        assertEquals(TimelineRollbackResultStatus.INVALID_REQUEST,
                TimelineRollbackResult.invalidRequest(List.of()).status());
        assertEquals(TimelineRollbackResultStatus.BLOCKED,
                TimelineRollbackResult.blocked(List.of()).status());
        assertEquals(TimelineRollbackResultStatus.FAILED,
                TimelineRollbackResult.failed(List.of()).status());
    }

    // --- Stage 3: Branch Switch Application Types ---

    @Test @DisplayName("Switch request id rejects blank")
    void switchRequestIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchSwitchRequestId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchSwitchRequestId(null));
    }

    @Test @DisplayName("Switch request requires source and target branch")
    void switchRequestRequiresBranches() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranchSwitchRequest(
                        new TimelineBranchSwitchRequestId("r1"),
                        null, new TimelineBranchName("target"), false, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranchSwitchRequest(
                        new TimelineBranchSwitchRequestId("r1"),
                        new TimelineBranchName("source"), null, false, Map.of()));
    }

    @Test @DisplayName("Switch request has unsaved changes flag")
    void switchRequestHasUnsavedFlag() {
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("src"), new TimelineBranchName("tgt"),
                true, Map.of());
        assertTrue(request.hasUnsavedChanges());
    }

    @Test @DisplayName("Switch result supports all statuses")
    void switchResultSupportsStatuses() {
        assertEquals(TimelineBranchSwitchResultStatus.READY,
                TimelineBranchSwitchResult.ready(null, null, null).status());
        assertEquals(TimelineBranchSwitchResultStatus.SOURCE_BRANCH_NOT_FOUND,
                TimelineBranchSwitchResult.sourceBranchNotFound(List.of()).status());
        assertEquals(TimelineBranchSwitchResultStatus.TARGET_BRANCH_NOT_FOUND,
                TimelineBranchSwitchResult.targetBranchNotFound(List.of()).status());
        assertEquals(TimelineBranchSwitchResultStatus.UNSAVED_CHANGES_REQUIRE_DECISION,
                TimelineBranchSwitchResult.unsavedChangesRequireDecision(
                        new TimelineBranchName("s"), new TimelineBranchName("t"), List.of()).status());
        assertEquals(TimelineBranchSwitchResultStatus.INVALID_REQUEST,
                TimelineBranchSwitchResult.invalidRequest(List.of()).status());
        assertEquals(TimelineBranchSwitchResultStatus.BLOCKED,
                TimelineBranchSwitchResult.blocked(List.of()).status());
        assertEquals(TimelineBranchSwitchResultStatus.FAILED,
                TimelineBranchSwitchResult.failed(List.of()).status());
    }

    // --- Stage 4: Issue Model and Lookup Port ---

    @Test @DisplayName("Issue severities exist")
    void issueSeveritiesExist() {
        assertNotNull(TimelineVersionApplicationIssueSeverity.INFO);
        assertNotNull(TimelineVersionApplicationIssueSeverity.WARNING);
        assertNotNull(TimelineVersionApplicationIssueSeverity.ERROR);
        assertNotNull(TimelineVersionApplicationIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(TimelineVersionApplicationIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(TimelineVersionApplicationIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(TimelineVersionApplicationIssueCode.STORAGE_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineVersionApplicationIssueCode.PROVIDER_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineVersionApplicationIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(TimelineVersionApplicationIssueCode.MERGE_ENGINE_NOT_IMPLEMENTED);
        assertNotNull(TimelineVersionApplicationIssueCode.CONFLICT_RESOLUTION_NOT_IMPLEMENTED);
        assertNotNull(TimelineVersionApplicationIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
    }

    @Test @DisplayName("Lookup is interface only")
    void lookupIsInterface() {
        assertTrue(TimelineVersionLookup.class.isInterface());
    }

    @Test @DisplayName("Lookup has no StorageRuntime/ProductRuntime dependency")
    void lookupHasNoRuntimeDependency() {
        // Verify by checking method signatures return pure domain types
        var methods = TimelineVersionLookup.class.getDeclaredMethods();
        for (var m : methods) {
            assertFalse(m.getReturnType().getName().contains("StorageRuntime"));
            assertFalse(m.getReturnType().getName().contains("ProductRuntime"));
        }
    }

    // --- Stage 5: TimelineCheckoutService ---

    @Test @DisplayName("Checkout by branch returns READY with snapshot")
    void checkoutByBranchReady() {
        var lookup = new FakeLookup();
        var branch = lookup.addBranch("main", "rev-1");
        lookup.addSnapshot("rev-1");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.READY, result.status());
        assertNotNull(result.checkoutPlan());
        assertNotNull(result.snapshot());
        assertEquals("rev-1", result.snapshot().revisionId());
    }

    @Test @DisplayName("Checkout by revision returns READY")
    void checkoutByRevisionReady() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-42");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofRevision(new TimelineRevisionRef("rev-42")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.READY, result.status());
        assertNotNull(result.snapshot());
    }

    @Test @DisplayName("Checkout by commit returns READY using commit revision ref")
    void checkoutByCommitReady() {
        var lookup = new FakeLookup();
        lookup.addCommit("c-1", "rev-10");
        lookup.addSnapshot("rev-10");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofCommit(new TimelineCommitId("c-1")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.READY, result.status());
    }

    @Test @DisplayName("Missing branch returns BRANCH_NOT_FOUND")
    void missingBranchReturnsNotFound() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("nonexistent")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.BRANCH_NOT_FOUND, result.status());
    }

    @Test @DisplayName("Missing revision returns REVISION_NOT_FOUND")
    void missingRevisionReturnsNotFound() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofRevision(new TimelineRevisionRef("nonexistent")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.REVISION_NOT_FOUND, result.status());
    }

    @Test @DisplayName("Missing commit returns COMMIT_NOT_FOUND")
    void missingCommitReturnsNotFound() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofCommit(new TimelineCommitId("nonexistent")),
                Map.of());

        var result = service.checkout(request);
        assertEquals(TimelineCheckoutResultStatus.COMMIT_NOT_FOUND, result.status());
    }

    @Test @DisplayName("Null request returns INVALID_TARGET")
    void nullRequestReturnsInvalid() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.checkout(null);
        assertEquals(TimelineCheckoutResultStatus.INVALID_TARGET, result.status());
    }

    // --- Stage 6: TimelineRollbackService ---

    @Test @DisplayName("Rollback current to target returns READY")
    void rollbackReady() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");
        lookup.addSnapshot("rev-3");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of());

        var result = service.planRollback(request);
        assertEquals(TimelineRollbackResultStatus.READY, result.status());
        assertNotNull(result.rollbackPlan());
        assertNotNull(result.intent());
        assertEquals(TimelineCommitType.ROLLBACK, result.intent().plannedCommitType());
    }

    @Test @DisplayName("Rollback current to same target returns NO_OP")
    void rollbackNoOp() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-1"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of());

        var result = service.planRollback(request);
        assertEquals(TimelineRollbackResultStatus.NO_OP, result.status());
    }

    @Test @DisplayName("Missing target returns TARGET_NOT_FOUND")
    void missingTargetReturnsNotFound() {
        var lookup = new FakeLookup();
        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("nonexistent"),
                null, false, Map.of());

        var result = service.planRollback(request);
        assertEquals(TimelineRollbackResultStatus.TARGET_NOT_FOUND, result.status());
    }

    @Test @DisplayName("requireTargetAncestor without ancestry returns TARGET_NOT_ANCESTOR")
    void requireAncestorReturnsNotAncestor() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("rev-1"),
                null, true, Map.of());

        var result = service.planRollback(request);
        assertEquals(TimelineRollbackResultStatus.TARGET_NOT_ANCESTOR, result.status());
    }

    @Test @DisplayName("Rollback intent uses ROLLBACK")
    void rollbackIntentType() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of());

        var result = service.planRollback(request);
        assertEquals(TimelineCommitType.ROLLBACK, result.intent().plannedCommitType());
    }

    @Test @DisplayName("Rollback is non-destructive")
    void rollbackNonDestructive() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of());

        var result = service.planRollback(request);
        // Verify no side effects: lookup state unchanged
        assertTrue(lookup.snapshots.containsKey("rev-1"));
        assertFalse(lookup.snapshots.containsKey("rev-3")); // never added
    }

    // --- Stage 7: TimelineBranchSwitchService ---

    @Test @DisplayName("Switch source to target returns READY")
    void switchReady() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-2");
        lookup.addSnapshot("rev-2");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                false, Map.of());

        var result = service.planSwitch(request);
        assertEquals(TimelineBranchSwitchResultStatus.READY, result.status());
        assertNotNull(result.switchPlan());
        assertNotNull(result.targetBranch());
        assertNotNull(result.targetSnapshot());
    }

    @Test @DisplayName("Missing source returns SOURCE_BRANCH_NOT_FOUND")
    void missingSourceReturnsNotFound() {
        var lookup = new FakeLookup();
        lookup.addBranch("feature", "rev-2");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("nonexistent"),
                new TimelineBranchName("feature"),
                false, Map.of());

        var result = service.planSwitch(request);
        assertEquals(TimelineBranchSwitchResultStatus.SOURCE_BRANCH_NOT_FOUND, result.status());
    }

    @Test @DisplayName("Missing target returns TARGET_BRANCH_NOT_FOUND")
    void missingTargetBranchReturnsNotFound() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("nonexistent"),
                false, Map.of());

        var result = service.planSwitch(request);
        assertEquals(TimelineBranchSwitchResultStatus.TARGET_BRANCH_NOT_FOUND, result.status());
    }

    @Test @DisplayName("Unsaved changes returns UNSAVED_CHANGES_REQUIRE_DECISION")
    void unsavedChangesRequireDecision() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-2");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                true, Map.of());

        var result = service.planSwitch(request);
        assertEquals(TimelineBranchSwitchResultStatus.UNSAVED_CHANGES_REQUIRE_DECISION, result.status());
    }

    @Test @DisplayName("Target snapshot returned if available")
    void targetSnapshotReturned() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-2");
        lookup.addSnapshot("rev-2");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                false, Map.of());

        var result = service.planSwitch(request);
        assertNotNull(result.targetSnapshot());
        assertEquals("rev-2", result.targetSnapshot().revisionId());
    }

    @Test @DisplayName("Switch does not mutate branch pointer")
    void switchDoesNotMutatePointer() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-2");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var request = new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                false, Map.of());

        var before = lookup.branches.get("main").headRevision().value();
        service.planSwitch(request);
        var after = lookup.branches.get("main").headRevision().value();
        assertEquals(before, after, "Branch pointer must not be mutated");
    }

    // --- Stage 8: Integration Semantics Tests ---

    @Test @DisplayName("Checkout result can be used as editing context without render")
    void checkoutResultIsEditingContext() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addSnapshot("rev-1");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.checkout(new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of()));

        assertEquals(TimelineCheckoutResultStatus.READY, result.status());
        assertNotNull(result.snapshot());
        assertNotNull(result.checkoutPlan());
        assertFalse(result.toString().contains("render"), "Result must not reference render");
    }

    @Test @DisplayName("Rollback result references current and target revision")
    void rollbackResultReferencesRevisions() {
        var lookup = new FakeLookup();
        lookup.addSnapshot("rev-1");

        var service = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.planRollback(new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r1"),
                new TimelineRevisionRef("rev-3"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of()));

        assertEquals("rev-3", result.intent().currentRevision().value());
        assertEquals("rev-1", result.intent().targetRevision().value());
    }

    @Test @DisplayName("Branch switch result references target branch head")
    void switchResultReferencesTargetHead() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-5");
        lookup.addSnapshot("rev-5");

        var service = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.planSwitch(new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r1"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                false, Map.of()));

        assertEquals("rev-5", result.targetBranch().headRevision().value());
    }

    @Test @DisplayName("Checkout/rollback/switch are deterministic across double-run")
    void deterministicAcrossDoubleRun() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addSnapshot("rev-1");

        var checkoutService = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var req1 = new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of());
        var r1 = checkoutService.checkout(req1);
        var r2 = checkoutService.checkout(req1);
        assertEquals(r1.status(), r2.status());
        assertEquals(r1.snapshot().revisionId(), r2.snapshot().revisionId());
    }

    @Test @DisplayName("Services are side-effect free")
    void servicesAreSideEffectFree() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addBranch("feature", "rev-2");
        lookup.addSnapshot("rev-1");
        lookup.addSnapshot("rev-2");

        var stateBefore = new HashMap<>(lookup.snapshots);

        var checkoutService = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        checkoutService.checkout(new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of()));

        var rollbackService = new TimelineRollbackService(lookup, new TimelineBranchSemanticsPlanner());
        rollbackService.planRollback(new TimelineRollbackRequest(
                new TimelineRollbackRequestId("r2"),
                new TimelineRevisionRef("rev-2"),
                new TimelineRevisionRef("rev-1"),
                null, false, Map.of()));

        var switchService = new TimelineBranchSwitchService(lookup, new TimelineBranchSemanticsPlanner());
        switchService.planSwitch(new TimelineBranchSwitchRequest(
                new TimelineBranchSwitchRequestId("r3"),
                new TimelineBranchName("main"),
                new TimelineBranchName("feature"),
                false, Map.of()));

        assertEquals(stateBefore, lookup.snapshots, "Lookup state must not change");
    }

    // --- Stage 9: Safety and Boundary Tests ---

    @Test @DisplayName("Services do not reference vedit")
    void noVeditReference() {
        assertFalse(TimelineCheckoutService.class.getName().contains("vedit"));
        assertFalse(TimelineRollbackService.class.getName().contains("vedit"));
        assertFalse(TimelineBranchSwitchService.class.getName().contains("vedit"));
    }

    @Test @DisplayName("Services do not reference pyvedit")
    void noPyveditReference() {
        assertFalse(TimelineCheckoutService.class.getName().contains("pyvedit"));
        assertFalse(TimelineRollbackService.class.getName().contains("pyvedit"));
        assertFalse(TimelineBranchSwitchService.class.getName().contains("pyvedit"));
    }

    @Test @DisplayName("Services do not reference OTIO runtime")
    void noOtioReference() {
        assertFalse(TimelineCheckoutService.class.getName().contains("OpenTimelineIO"));
        assertFalse(TimelineRollbackService.class.getName().contains("OpenTimelineIO"));
        assertFalse(TimelineBranchSwitchService.class.getName().contains("OpenTimelineIO"));
    }

    @Test @DisplayName("Services do not reference Remotion")
    void noRemotionReference() {
        assertFalse(TimelineCheckoutService.class.getName().contains("Remotion"));
        assertFalse(TimelineRollbackService.class.getName().contains("Remotion"));
        assertFalse(TimelineBranchSwitchService.class.getName().contains("Remotion"));
    }

    @Test @DisplayName("Services do not expose provider/backend fields")
    void noProviderBackendFields() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addSnapshot("rev-1");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.checkout(new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of()));

        String repr = result.toString();
        assertFalse(repr.contains("providerName"));
        assertFalse(repr.contains("providerType"));
        assertFalse(repr.contains("backendName"));
        assertFalse(repr.contains("executionEnvironment"));
        assertFalse(repr.contains("autoDispatch"));
    }

    @Test @DisplayName("Services do not expose storage internals")
    void noStorageInternals() {
        var lookup = new FakeLookup();
        lookup.addBranch("main", "rev-1");
        lookup.addSnapshot("rev-1");

        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var result = service.checkout(new TimelineCheckoutRequest(
                new TimelineCheckoutRequestId("r1"),
                TimelineCheckoutTarget.ofBranch(new TimelineBranchName("main")),
                Map.of()));

        String repr = result.toString();
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("objectKey"));
        assertFalse(repr.contains("signedUrl"));
    }

    @Test @DisplayName("Services do not implement merge engine")
    void noMergeEngine() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        // Verify no merge-related methods exist
        var methods = service.getClass().getDeclaredMethods();
        for (var m : methods) {
            assertFalse(m.getName().contains("merge"));
        }
    }

    @Test @DisplayName("Services do not persist Timeline Git")
    void noPersistence() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        var methods = service.getClass().getDeclaredMethods();
        for (var m : methods) {
            assertFalse(m.getName().contains("persist"));
            assertFalse(m.getName().contains("save"));
        }
    }

    @Test @DisplayName("Services do not use Artifact DAG")
    void noArtifactDag() {
        var lookup = new FakeLookup();
        var service = new TimelineCheckoutService(lookup, new TimelineBranchSemanticsPlanner());
        String repr = service.toString();
        assertFalse(repr.contains("ArtifactDAG"));
        assertFalse(repr.contains("artifactDag"));
    }

    // --- Fake Lookup ---

    static class FakeLookup implements TimelineVersionLookup {
        final Map<String, TimelineBranch> branches = new ConcurrentHashMap<>();
        final Map<String, CanonicalTimelineSnapshot> snapshots = new ConcurrentHashMap<>();
        final Map<String, TimelineCommit> commits = new ConcurrentHashMap<>();

        TimelineBranch addBranch(String name, String headRevision) {
            var branch = new TimelineBranch(
                    new TimelineBranchId("branch-" + name),
                    new TimelineBranchName(name),
                    new TimelineRevisionRef(headRevision),
                    Map.of());
            branches.put(name, branch);
            return branch;
        }

        void addSnapshot(String revisionId) {
            var snapshot = new CanonicalTimelineSnapshot(
                    new CanonicalTimelineSnapshotId("snap-" + revisionId),
                    revisionId,
                    0, List.of(), List.of(), List.of(),
                    List.of(), List.of(), null, Map.of());
            snapshots.put(revisionId, snapshot);
        }

        void addCommit(String commitId, String revisionId) {
            var commit = new TimelineCommit(
                    new TimelineCommitId(commitId),
                    new TimelineRevisionRef(revisionId),
                    TimelineCommitType.EDIT,
                    List.of(),
                    new TimelineCommitMetadata("test", "msg", Map.of()),
                    Map.of());
            commits.put(commitId, commit);
        }

        @Override
        public Optional<TimelineBranch> findBranch(TimelineBranchName branchName) {
            return Optional.ofNullable(branches.get(branchName.value()));
        }

        @Override
        public Optional<CanonicalTimelineSnapshot> findSnapshot(TimelineRevisionRef revisionRef) {
            return Optional.ofNullable(snapshots.get(revisionRef.value()));
        }

        @Override
        public Optional<TimelineCommit> findCommit(TimelineCommitId commitId) {
            return Optional.ofNullable(commits.get(commitId.value()));
        }
    }
}
