package com.example.platform.timeline.api.revision;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.Optional;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineRevisionCommands {
TimelineRevision saveRevision(
            TimelineMutationContext context,
            String expectedCurrentRevisionId,
            TimelineDocument document);

RevisionWriteResult saveRevisionForCommand(
            TimelineMutationContext context,
            RevisionRef targetRef,
            String expectedCurrentRevisionId,
            TimelineDocument document,
            RevisionWriteCommand command);

/**
 * Command-aware write with atomic commit provenance ({@code change_summary_json})
 * and optional {@code message}. Both columns are written in the SAME transaction
 * as the revision, snapshot, parent edge and ref CAS; no later transaction may
 * backfill them.
 */
RevisionWriteResult saveRevisionForCommand(
            TimelineMutationContext context,
            RevisionRef targetRef,
            String expectedCurrentRevisionId,
            TimelineDocument document,
            RevisionWriteCommand command,
            RevisionProvenance provenance);

RevisionWriteResult recordNoOpCommand(
            TimelineMutationContext context,
            RevisionRef targetRef,
            String expectedCurrentRevisionId,
            String baseTimelineContentHash,
            RevisionWriteCommand command);

TimelineRevision saveRevisionWithEffects(
            TimelineMutationContext context, String expectedCurrentRevisionId,
            TimelineDocument document,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance> effects,
            java.util.List<com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition> definitions);

TimelineRevision saveMergeRevision(
            TimelineMutationContext context, String expectedMainHead,
            String sourceRevisionId, String mergeBaseRevisionId,
            TimelineDocument document);

public record RevisionWriteCommand(
            String commandId,
            String planDigest,
            String fingerprint,
            String commandDomain,
            String tenantId,
            boolean preserveParentEffects) {
        public RevisionWriteCommand(String commandId, String planDigest, String fingerprint,
                                    String commandDomain, String tenantId) {
            this(commandId, planDigest, fingerprint, commandDomain, tenantId, false);
        }

        public RevisionWriteCommand {
            if (commandId == null || commandId.isBlank()
                    || planDigest == null || planDigest.isBlank()
                    || fingerprint == null || fingerprint.isBlank()
                    || commandDomain == null || commandDomain.isBlank()
                    || tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("complete revision write command required");
            }
        }
    }

public record RevisionWriteResult(
            String revisionId,
            String parentRevisionId,
            String timelineContentHash,
            boolean replayed) {
    }

TimelineRevision restoreRevision(
            TimelineMutationContext context,
            String historicalRevisionId,
            String expectedCurrentRevisionId);

/** Loads verified immutable historical state for Operation planning within the exact project. */
record RestoreSource(TimelineDocument document, String semanticDigest) {}

Optional<RestoreSource> findRestoreSource(String projectId, String tenantId, String revisionId);

RevisionWriteResult saveRestoreForCommand(TimelineMutationContext context, RevisionRef targetRef,
            String expectedCurrentRevisionId, String sourceRevisionId, String candidateHash,
            RevisionWriteCommand command, RevisionProvenance provenance);

String findEditingRefHead(RevisionRef ref);

/** Validates effects inherited from the exact scoped parent before confirming a branch edit. */
void validateEditingBranchCandidate(RevisionRef ref, String parentRevisionId, TimelineDocument document);

/** Rejects paste of separate effect bindings that cannot be cloned by this operation. */
void validatePasteSources(RevisionRef ref, String revisionId,
                          java.util.List<com.example.platform.timeline.canonical.TimelineClipId> clipIds);

record CreatedEditingRef(String refId, String sourceRevisionId, boolean replayed) {}

Optional<CreatedEditingRef> createEditingRef(TimelineMutationContext context, String refId,
                                             String sourceRevisionId, String commandId);

TimelineRevision findById(String tenantId, String revisionId);

Optional<TimelineDocument> findPayloadDocument(String tenantId, String revisionId);
}
