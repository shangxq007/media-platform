package com.example.platform.timeline.app;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.patch.PatchApplicationResult;
import com.example.platform.timeline.patch.PatchErrorCode;
import com.example.platform.timeline.patch.PatchError;
import com.example.platform.timeline.patch.TimelinePatch;
import com.example.platform.timeline.patch.TimelinePatchEngine;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for atomic patch application.
 * Loads base revision, validates, applies patch, creates new revision.
 */
@Service
public class TimelinePatchApplicationService {

    private final TimelineRevisionSaveService revisionSaveService;
    private final ProductCurrentRevisionService currentRevisionService;
    private final TimelineContentDigester contentDigester;

    public TimelinePatchApplicationService(TimelineRevisionSaveService revisionSaveService,
                                           ProductCurrentRevisionService currentRevisionService,
                                           TimelineContentDigester contentDigester) {
        this.revisionSaveService = revisionSaveService;
        this.currentRevisionService = currentRevisionService;
        this.contentDigester = contentDigester;
    }

    @Transactional
    public PatchApplyResult apply(TimelinePatch patch) {
        // Load base revision
        TimelineRevision baseRevision = revisionSaveService.findById(patch.baseRevisionId());
        if (baseRevision == null) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_REVISION_NOT_FOUND,
                    "Base revision not found: " + patch.baseRevisionId(), null, null));
        }

        // Validate product isolation
        if (!patch.productId().equals(baseRevision.productId())) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_CROSS_PRODUCT_NOT_ALLOWED,
                    "Cross-product patch not allowed", null, null));
        }

        // Validate base is current
        String currentRevisionId = currentRevisionService.getCurrentRevisionId(patch.productId());
        if (!patch.baseRevisionId().equals(currentRevisionId)) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_BASE_NOT_CURRENT,
                    "Base revision is not current", null, null));
        }

        // Validate expected current
        if (!patch.expectedCurrentRevisionId().equals(currentRevisionId)) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_REVISION_CONFLICT,
                    "Expected current revision mismatch", null, null));
        }

        // Validate base digest
        if (!patch.baseContentDigest().equals(baseRevision.contentDigest())) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_BASE_DIGEST_MISMATCH,
                    "Base content digest mismatch", null, null));
        }

        // Validate schema
        if (!patch.timelineSchemaVersion().equals(baseRevision.timelineSchemaVersion())) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_SCHEMA_INCOMPATIBLE,
                    "Schema version incompatible", null, null));
        }

        // PPHR-BIC: resolve the governed persisted Timeline payload through the existing
        // snapshot authority (TimelineSnapshotService via TimelineRevisionSaveService)
        // before invoking the Patch engine. findById intentionally does not hydrate
        // canonicalTimeline; the hydration is explicit and local to the Patch flow.
        TimelineDocument baseDocument = revisionSaveService.findPayloadDocument(patch.baseRevisionId())
                .orElse(null);
        if (baseDocument == null) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                    "Base revision has no canonical timeline", null, null));
        }

        // Apply patch using pure engine
        PatchApplicationResult result = TimelinePatchEngine.apply(baseDocument, patch);

        if (result instanceof PatchApplicationResult.Failure failure) {
            return PatchApplyResult.failure(failure.errors().get(0));
        }

        TimelineDocument resultDocument = ((PatchApplicationResult.Success) result).document();

        // Check for no changes
        String resultDigest = contentDigester.digest(resultDocument);
        if (resultDigest.equals(patch.baseContentDigest())) {
            return PatchApplyResult.noChanges(patch.baseRevisionId());
        }

        // Validate expected result digest
        if (patch.hasExpectedResultDigest() && !resultDigest.equals(patch.expectedResultDigest())) {
            return PatchApplyResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_RESULT_DIGEST_MISMATCH,
                    "Result digest mismatch", null, null));
        }

        // Create new revision atomically
        TimelineRevision newRevision = revisionSaveService.saveRevision(
                patch.productId(), patch.expectedCurrentRevisionId(), resultDocument, "patch-service");

        return PatchApplyResult.success(newRevision.revisionId(), patch.baseRevisionId(), resultDigest);
    }

    @Transactional(readOnly = true)
    public PatchPreviewResult preview(TimelinePatch patch) {
        // Load base revision
        TimelineRevision baseRevision = revisionSaveService.findById(patch.baseRevisionId());
        if (baseRevision == null) {
            return PatchPreviewResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_REVISION_NOT_FOUND,
                    "Base revision not found: " + patch.baseRevisionId(), null, null));
        }

        // Validate product isolation
        if (!patch.productId().equals(baseRevision.productId())) {
            return PatchPreviewResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_CROSS_PRODUCT_NOT_ALLOWED,
                    "Cross-product patch not allowed", null, null));
        }

        // Validate base digest
        if (!patch.baseContentDigest().equals(baseRevision.contentDigest())) {
            return PatchPreviewResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_BASE_DIGEST_MISMATCH,
                    "Base content digest mismatch", null, null));
        }

        // PPHR-BIC: resolve the governed persisted Timeline payload through the existing
        // snapshot authority before invoking the Patch engine (preview: dry run only).
        TimelineDocument baseDocument = revisionSaveService.findPayloadDocument(patch.baseRevisionId())
                .orElse(null);
        if (baseDocument == null) {
            return PatchPreviewResult.failure(new PatchError(PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                    "Base revision has no canonical timeline", null, null));
        }

        // Apply patch using pure engine
        PatchApplicationResult result = TimelinePatchEngine.apply(baseDocument, patch);

        if (result instanceof PatchApplicationResult.Failure failure) {
            return PatchPreviewResult.failure(failure.errors().get(0));
        }

        TimelineDocument resultDocument = ((PatchApplicationResult.Success) result).document();
        String resultDigest = contentDigester.digest(resultDocument);

        return PatchPreviewResult.success(resultDigest);
    }
}
