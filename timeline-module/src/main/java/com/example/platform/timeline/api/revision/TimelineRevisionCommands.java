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
            String tenantId) {
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

TimelineRevision findById(String tenantId, String revisionId);

Optional<TimelineDocument> findPayloadDocument(String tenantId, String revisionId);
}
