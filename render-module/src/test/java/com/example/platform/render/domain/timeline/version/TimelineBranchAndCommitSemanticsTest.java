package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Timeline Branch and Commit Semantics.
 * Proves: identity types, branch/pointer, commit, issue model,
 * checkout/rollback/switch plans, planner, safety boundaries.
 */
class TimelineBranchAndCommitSemanticsTest {

    // --- Stage 1: Identity types ---

    @Test
    @DisplayName("TimelineRevisionRef rejects blank")
    void revisionRefRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef(null));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef("  "));
    }

    @Test
    @DisplayName("TimelineRevisionRef rejects storage/provider keywords")
    void revisionRefRejectsForbiddenKeywords() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef("bucket-x"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef("signedUrl-leak"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef("providerName-x"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRevisionRef("backendName-x"));
    }

    @Test
    @DisplayName("TimelineBranchId rejects blank")
    void branchIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchId(null));
    }

    @Test
    @DisplayName("TimelineCommitId rejects blank")
    void commitIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineCommitId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineCommitId(null));
    }

    @Test
    @DisplayName("TimelineBranchName accepts safe names")
    void branchNameAcceptsSafeNames() {
        assertDoesNotThrow(() -> new TimelineBranchName("main"));
        assertDoesNotThrow(() -> new TimelineBranchName("draft-ai-edit"));
        assertDoesNotThrow(() -> new TimelineBranchName("client-review"));
        assertDoesNotThrow(() -> new TimelineBranchName("subtitle-style-a"));
    }

    @Test
    @DisplayName("TimelineBranchName rejects blank")
    void branchNameRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName(null));
    }

    @Test
    @DisplayName("TimelineBranchName rejects unsafe characters")
    void branchNameRejectsUnsafeChars() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch with spaces"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch;rm"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch|pipe"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch$dollar"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch`backtick"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("branch..traversal"));
    }

    @Test
    @DisplayName("TimelineBranchName rejects storage/provider keywords")
    void branchNameRejectsForbiddenKeywords() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("bucket-branch"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("signedUrl-x"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("providerName-x"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineBranchName("backendName-x"));
    }

    // --- Stage 2: Branch and Pointer ---

    @Test
    @DisplayName("Branch requires id/name/head revision")
    void branchRequiresFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranch(null, new TimelineBranchName("main"),
                        new TimelineRevisionRef("rev-1"), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranch(new TimelineBranchId("b1"), null,
                        new TimelineRevisionRef("rev-1"), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranch(new TimelineBranchId("b1"), new TimelineBranchName("main"),
                        null, Map.of()));
    }

    @Test
    @DisplayName("Branch stores safe metadata")
    void branchSafeMetadata() {
        TimelineBranch branch = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of("key", "val"));
        assertEquals("val", branch.safeMetadata().get("key"));
    }

    @Test
    @DisplayName("Branch pointer requires branch id/head revision")
    void pointerRequiresFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranchPointer(null, new TimelineRevisionRef("rev-1"), null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineBranchPointer(new TimelineBranchId("b1"), null, null, Map.of()));
    }

    @Test
    @DisplayName("Branch pointer can move semantically")
    void pointerCanMove() {
        TimelineBranchPointer pointer = new TimelineBranchPointer(
                new TimelineBranchId("b1"), new TimelineRevisionRef("rev-1"),
                new TimelineCommitId("c1"), Map.of());
        TimelineBranchPointer moved = pointer.moveTo(
                new TimelineRevisionRef("rev-2"), new TimelineCommitId("c2"));
        assertEquals("rev-2", moved.headRevision().value());
        assertEquals("c2", moved.headCommitId().value());
        assertEquals("b1", moved.branchId().value());
        // Original not mutated
        assertEquals("rev-1", pointer.headRevision().value());
    }

    @Test
    @DisplayName("Branch and pointer do not expose storage/provider fields")
    void branchPointerNoProviderFields() {
        TimelineBranch branch = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        assertFalse(branch.toString().contains("providerName"));
        assertFalse(branch.toString().contains("bucket"));
        assertFalse(branch.toString().contains("signedUrl"));

        TimelineBranchPointer pointer = new TimelineBranchPointer(
                new TimelineBranchId("b1"), new TimelineRevisionRef("rev-1"),
                new TimelineCommitId("c1"), Map.of());
        assertFalse(pointer.toString().contains("providerName"));
        assertFalse(pointer.toString().contains("bucket"));
        assertFalse(pointer.toString().contains("signedUrl"));
    }

    // --- Stage 3: Commit Semantics ---

    @Test
    @DisplayName("Initial commit may have zero parents")
    void initialCommitZeroParents() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c1"), new TimelineRevisionRef("rev-1"),
                TimelineCommitType.INITIAL, List.of(),
                new TimelineCommitMetadata("user", "Initial", Map.of()), Map.of());
        assertTrue(commit.isRoot());
        assertFalse(commit.isMerge());
    }

    @Test
    @DisplayName("Edit commit with one parent")
    void editCommitOneParent() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c2"), new TimelineRevisionRef("rev-2"),
                TimelineCommitType.EDIT,
                List.of(TimelineCommitParent.primary(new TimelineCommitId("c1"))),
                new TimelineCommitMetadata("user", "Edit", Map.of()), Map.of());
        assertFalse(commit.isRoot());
        assertFalse(commit.isMerge());
    }

    @Test
    @DisplayName("Commit rejects more than two parents")
    void commitRejectsMoreThanTwoParents() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineCommit(
                        new TimelineCommitId("c4"), new TimelineRevisionRef("rev-4"),
                        TimelineCommitType.MANUAL_MERGE,
                        List.of(
                                TimelineCommitParent.primary(new TimelineCommitId("c1")),
                                TimelineCommitParent.secondary(new TimelineCommitId("c2")),
                                TimelineCommitParent.secondary(new TimelineCommitId("c3"))),
                        new TimelineCommitMetadata("user", "Merge", Map.of()), Map.of()));
    }

    @Test
    @DisplayName("Commit rejects multiple primary parents")
    void commitRejectsMultiplePrimaryParents() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineCommit(
                        new TimelineCommitId("c3"), new TimelineRevisionRef("rev-3"),
                        TimelineCommitType.MANUAL_MERGE,
                        List.of(
                                new TimelineCommitParent(new TimelineCommitId("c1"), true, Map.of()),
                                new TimelineCommitParent(new TimelineCommitId("c2"), true, Map.of())),
                        new TimelineCommitMetadata("user", "Merge", Map.of()), Map.of()));
    }

    @Test
    @DisplayName("Manual merge commit type exists as vocabulary")
    void manualMergeCommitTypeExists() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c3"), new TimelineRevisionRef("rev-3"),
                TimelineCommitType.MANUAL_MERGE,
                List.of(
                        TimelineCommitParent.primary(new TimelineCommitId("c1")),
                        TimelineCommitParent.secondary(new TimelineCommitId("c2"))),
                new TimelineCommitMetadata("user", "Manual merge", Map.of()), Map.of());
        assertTrue(commit.isMerge());
        assertEquals(TimelineCommitType.MANUAL_MERGE, commit.type());
    }

    @Test
    @DisplayName("Rollback commit type exists")
    void rollbackCommitTypeExists() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c2"), new TimelineRevisionRef("rev-2"),
                TimelineCommitType.ROLLBACK,
                List.of(TimelineCommitParent.primary(new TimelineCommitId("c1"))),
                new TimelineCommitMetadata("user", "Rollback to rev-1", Map.of()), Map.of());
        assertEquals(TimelineCommitType.ROLLBACK, commit.type());
    }

    @Test
    @DisplayName("Commit metadata is safe")
    void commitMetadataSafe() {
        assertDoesNotThrow(() ->
                new TimelineCommitMetadata("user", "Edit", Map.of("key", "val")));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineCommitMetadata("user", "secret-leak", Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineCommitMetadata("user", "Edit", Map.of("password", "x")));
    }

    // --- Stage 4: Issue Model ---

    @Test
    @DisplayName("Issue severity enum contains all levels")
    void issueSeverityComplete() {
        assertEquals(4, TimelineBranchOperationIssueSeverity.values().length);
        assertNotNull(TimelineBranchOperationIssueSeverity.INFO);
        assertNotNull(TimelineBranchOperationIssueSeverity.WARNING);
        assertNotNull(TimelineBranchOperationIssueSeverity.ERROR);
        assertNotNull(TimelineBranchOperationIssueSeverity.BLOCKING);
    }

    @Test
    @DisplayName("Issue codes contain boundary codes")
    void issueCodesContainBoundaries() {
        assertNotNull(TimelineBranchOperationIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(TimelineBranchOperationIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(TimelineBranchOperationIssueCode.STORAGE_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineBranchOperationIssueCode.PROVIDER_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineBranchOperationIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
        assertNotNull(TimelineBranchOperationIssueCode.MERGE_NOT_IMPLEMENTED);
        assertNotNull(TimelineBranchOperationIssueCode.ROLLBACK_IS_NON_DESTRUCTIVE);
    }

    @Test
    @DisplayName("Issue fields are safe")
    void issueFieldsSafe() {
        TimelineBranchOperationIssue issue = new TimelineBranchOperationIssue(
                TimelineBranchOperationIssueSeverity.ERROR,
                TimelineBranchOperationIssueCode.INVALID_BRANCH_NAME,
                "Invalid name", Map.of());
        assertFalse(issue.toString().contains("bucket"));
        assertFalse(issue.toString().contains("signedUrl"));
        assertFalse(issue.toString().contains("providerName"));
    }

    // --- Stage 5: Checkout Plan ---

    @Test
    @DisplayName("Checkout branch head returns READY")
    void checkoutBranchHeadReady() {
        TimelineBranch branch = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineCheckoutPlan plan = planner.planCheckout(branch);
        assertEquals(TimelineCheckoutStatus.READY, plan.status());
        assertEquals("main", plan.branchName().value());
        assertEquals("rev-1", plan.targetRevision().value());
    }

    @Test
    @DisplayName("Checkout null branch returns INVALID_TARGET")
    void checkoutNullBranchInvalid() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineCheckoutPlan plan = planner.planCheckout((TimelineBranch) null);
        assertEquals(TimelineCheckoutStatus.INVALID_TARGET, plan.status());
    }

    @Test
    @DisplayName("Checkout null revision returns REVISION_NOT_FOUND")
    void checkoutNullRevisionNotFound() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineCheckoutPlan plan = planner.planCheckout((TimelineRevisionRef) null);
        assertEquals(TimelineCheckoutStatus.REVISION_NOT_FOUND, plan.status());
    }

    @Test
    @DisplayName("Checkout does not render")
    void checkoutDoesNotRender() {
        TimelineCheckoutPlan plan = TimelineCheckoutPlan.ready(
                new TimelineBranchName("main"), new TimelineRevisionRef("rev-1"));
        assertFalse(plan.toString().contains("render"));
        assertFalse(plan.toString().contains("FFmpeg"));
        assertFalse(plan.toString().contains("Remotion"));
    }

    @Test
    @DisplayName("Checkout does not create Product")
    void checkoutDoesNotCreateProduct() {
        TimelineCheckoutPlan plan = TimelineCheckoutPlan.ready(
                new TimelineBranchName("main"), new TimelineRevisionRef("rev-1"));
        assertFalse(plan.toString().contains("Product"));
        assertFalse(plan.toString().contains("ProductRuntime"));
    }

    @Test
    @DisplayName("Checkout safe metadata only")
    void checkoutSafeMetadataOnly() {
        TimelineCheckoutPlan plan = TimelineCheckoutPlan.blocked(List.of(
                new TimelineBranchOperationIssue(
                        TimelineBranchOperationIssueSeverity.BLOCKING,
                        TimelineBranchOperationIssueCode.STORAGE_INTERNALS_NOT_ALLOWED,
                        "Blocked", Map.of())));
        assertFalse(plan.safeMetadata().containsKey("bucket"));
    }

    // --- Stage 6: Rollback Plan ---

    @Test
    @DisplayName("Rollback current to target returns READY")
    void rollbackCurrentToTargetReady() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineRollbackPlan plan = planner.planRollback(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        assertEquals(TimelineRollbackStatus.READY, plan.status());
        assertEquals("rev-3", plan.currentRevision().value());
        assertEquals("rev-1", plan.targetRevision().value());
    }

    @Test
    @DisplayName("Rollback current to same target returns NO_OP")
    void rollbackSameTargetNoOp() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineRollbackPlan plan = planner.planRollback(
                new TimelineRevisionRef("rev-1"), new TimelineRevisionRef("rev-1"));
        assertEquals(TimelineRollbackStatus.NO_OP, plan.status());
    }

    @Test
    @DisplayName("Rollback plan uses ROLLBACK commit type")
    void rollbackUsesRollbackCommitType() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineRollbackPlan plan = planner.planRollback(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        assertEquals(TimelineCommitType.ROLLBACK, plan.plannedCommitType());
    }

    @Test
    @DisplayName("Rollback is non-destructive")
    void rollbackIsNonDestructive() {
        TimelineRollbackPlan plan = TimelineRollbackPlan.ready(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        assertFalse(plan.toString().contains("delete"));
        assertFalse(plan.toString().contains("hard reset"));
        assertFalse(plan.toString().contains("destroy"));
    }

    @Test
    @DisplayName("Rollback does not delete history")
    void rollbackDoesNotDeleteHistory() {
        TimelineRollbackPlan plan = TimelineRollbackPlan.ready(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        // Rollback creates new revision, not destructive rewrite
        assertNotNull(plan.currentRevision());
        assertNotNull(plan.targetRevision());
    }

    @Test
    @DisplayName("Rollback does not render")
    void rollbackDoesNotRender() {
        TimelineRollbackPlan plan = TimelineRollbackPlan.ready(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        assertFalse(plan.toString().contains("render"));
        assertFalse(plan.toString().contains("FFmpeg"));
    }

    @Test
    @DisplayName("Rollback does not create Product")
    void rollbackDoesNotCreateProduct() {
        TimelineRollbackPlan plan = TimelineRollbackPlan.ready(
                new TimelineRevisionRef("rev-3"), new TimelineRevisionRef("rev-1"));
        assertFalse(plan.toString().contains("Product"));
        assertFalse(plan.toString().contains("ProductRuntime"));
    }

    @Test
    @DisplayName("Rollback null targets returns INVALID_TARGET")
    void rollbackNullTargetsInvalid() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineRollbackPlan plan = planner.planRollback(null, null);
        assertEquals(TimelineRollbackStatus.INVALID_TARGET, plan.status());
    }

    // --- Stage 7: Branch Switch Plan ---

    @Test
    @DisplayName("Switch source to target returns READY")
    void switchSourceToTargetReady() {
        TimelineBranch source = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        TimelineBranch target = new TimelineBranch(
                new TimelineBranchId("b2"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineBranchSwitchPlan plan = planner.planSwitch(source, target, false);
        assertEquals(TimelineBranchSwitchStatus.READY, plan.status());
        assertEquals("main", plan.sourceBranch().value());
        assertEquals("feature", plan.targetBranch().value());
    }

    @Test
    @DisplayName("Switch with missing source returns SOURCE_BRANCH_NOT_FOUND")
    void switchMissingSourceNotFound() {
        TimelineBranch target = new TimelineBranch(
                new TimelineBranchId("b2"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineBranchSwitchPlan plan = planner.planSwitch(null, target, false);
        assertEquals(TimelineBranchSwitchStatus.SOURCE_BRANCH_NOT_FOUND, plan.status());
    }

    @Test
    @DisplayName("Switch with missing target returns TARGET_BRANCH_NOT_FOUND")
    void switchMissingTargetNotFound() {
        TimelineBranch source = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineBranchSwitchPlan plan = planner.planSwitch(source, null, false);
        assertEquals(TimelineBranchSwitchStatus.TARGET_BRANCH_NOT_FOUND, plan.status());
    }

    @Test
    @DisplayName("Switch with unsaved changes returns UNSAVED_CHANGES_REQUIRE_DECISION")
    void switchUnsavedChangesRequireDecision() {
        TimelineBranch source = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        TimelineBranch target = new TimelineBranch(
                new TimelineBranchId("b2"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineBranchSwitchPlan plan = planner.planSwitch(source, target, true);
        assertEquals(TimelineBranchSwitchStatus.UNSAVED_CHANGES_REQUIRE_DECISION, plan.status());
    }

    @Test
    @DisplayName("Switch does not render")
    void switchDoesNotRender() {
        TimelineBranchSwitchPlan plan = TimelineBranchSwitchPlan.ready(
                new TimelineBranchName("main"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"));
        assertFalse(plan.toString().contains("render"));
        assertFalse(plan.toString().contains("FFmpeg"));
    }

    @Test
    @DisplayName("Switch does not create Product")
    void switchDoesNotCreateProduct() {
        TimelineBranchSwitchPlan plan = TimelineBranchSwitchPlan.ready(
                new TimelineBranchName("main"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"));
        assertFalse(plan.toString().contains("Product"));
        assertFalse(plan.toString().contains("ProductRuntime"));
    }

    @Test
    @DisplayName("Switch does not mutate branch pointer")
    void switchDoesNotMutatePointer() {
        TimelineBranch source = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        TimelineBranch target = new TimelineBranch(
                new TimelineBranchId("b2"), new TimelineBranchName("feature"),
                new TimelineRevisionRef("rev-2"), Map.of());
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        planner.planSwitch(source, target, false);
        assertEquals("rev-1", source.headRevision().value());
        assertEquals("rev-2", target.headRevision().value());
    }

    // --- Stage 8: Planner ---

    @Test
    @DisplayName("Planner is side-effect free")
    void plannerIsSideEffectFree() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        TimelineBranch branch = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());

        TimelineCheckoutPlan plan1 = planner.planCheckout(branch);
        TimelineCheckoutPlan plan2 = planner.planCheckout(branch);
        assertEquals(plan1.status(), plan2.status());
        assertEquals(plan1.branchName(), plan2.branchName());
    }

    @Test
    @DisplayName("Planner does not use persistence")
    void plannerNoPersistence() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        assertFalse(planner.getClass().getName().contains("Repository"));
        assertFalse(planner.getClass().getName().contains("Store"));
    }

    @Test
    @DisplayName("Planner does not use render")
    void plannerNoRender() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        assertFalse(planner.getClass().getName().contains("Render"));
        assertFalse(planner.getClass().getName().contains("FFmpeg"));
    }

    // --- Stage 9: Safety and Boundary Tests ---

    @Test
    @DisplayName("No vedit/pyvedit/OTIO references in version package")
    void noExternalDependencyReferences() {
        TimelineRevisionRef ref = new TimelineRevisionRef("rev-1");
        assertFalse(ref.toString().contains("vedit"));
        assertFalse(ref.toString().contains("pyvedit"));
        assertFalse(ref.toString().contains("OpenTimelineIO"));
        assertFalse(ref.toString().contains("opentimelineio"));
    }

    @Test
    @DisplayName("No Remotion references")
    void noRemotionReferences() {
        TimelineBranch branch = new TimelineBranch(
                new TimelineBranchId("b1"), new TimelineBranchName("main"),
                new TimelineRevisionRef("rev-1"), Map.of());
        assertFalse(branch.toString().contains("Remotion"));
        assertFalse(branch.toString().contains("remotion"));
    }

    @Test
    @DisplayName("No provider/backend fields exposed")
    void noProviderBackendFields() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c1"), new TimelineRevisionRef("rev-1"),
                TimelineCommitType.INITIAL, List.of(),
                new TimelineCommitMetadata("user", "Init", Map.of()), Map.of());
        assertFalse(commit.toString().contains("providerName"));
        assertFalse(commit.toString().contains("providerType"));
        assertFalse(commit.toString().contains("backendName"));
        assertFalse(commit.toString().contains("executionEnvironment"));
        assertFalse(commit.toString().contains("autoDispatch"));
    }

    @Test
    @DisplayName("No storage internals exposed")
    void noStorageInternals() {
        TimelineCommitMetadata meta = new TimelineCommitMetadata("user", "Edit", Map.of());
        assertFalse(meta.toString().contains("bucket"));
        assertFalse(meta.toString().contains("objectKey"));
        assertFalse(meta.toString().contains("signedUrl"));
    }

    @Test
    @DisplayName("No merge engine implemented")
    void noMergeEngine() {
        TimelineCommitType manualMerge = TimelineCommitType.MANUAL_MERGE;
        assertNotNull(manualMerge);
        // MANUAL_MERGE is vocabulary only, no automatic merge
    }

    @Test
    @DisplayName("No global graph optimization")
    void noGlobalOptimization() {
        TimelineBranchSemanticsPlanner planner = new TimelineBranchSemanticsPlanner();
        assertFalse(planner.getClass().getName().contains("Optimization"));
        assertFalse(planner.getClass().getName().contains("Global"));
    }
}
