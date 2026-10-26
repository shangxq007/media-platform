package com.example.platform.workerfabric.reuse;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompletionArtifactIdentityTest {
    @Test void differentCommittedArtifactCannotStageOrCompleteThePublication() {
        var index=mock(ArtifactReuseIndexPort.class);
        var authority=mock(CompletionAuthorityPort.class);
        var publication=mock(ReusableArtifactPublication.class,RETURNS_DEEP_STUBS);
        when(publication.record().artifactPin().artifactId()).thenReturn(new ArtifactId("expected-artifact"));
        var result=new FencedReuseCompletionOrchestrator(index,authority).complete(publication,
                mock(CompletionEvidence.class),new ArtifactCommitEvidence(new ArtifactId("other-artifact")));
        assertThat(result.completionDecision()).isEqualTo(CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED);
        assertThat(result.publicationResult()).isEqualTo(ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED);
        verifyNoInteractions(index,authority);
    }

    @Test void pendingConflictNeverCallsNativeCompletion() {
        var index=mock(ArtifactReuseIndexPort.class);var authority=mock(CompletionAuthorityPort.class);
        var publication=publication();when(index.stageWinningPublication(publication)).thenReturn(ReusePublicationResult.CONFLICT_REJECTED);
        when(index.lookup(anyString(),any())).thenReturn(java.util.Optional.empty());
        var result=new FencedReuseCompletionOrchestrator(index,authority).complete(publication,mock(CompletionEvidence.class),new ArtifactCommitEvidence(new ArtifactId("current-artifact")));
        assertThat(result.publicationResult()).isEqualTo(ReusePublicationResult.CONFLICT_REJECTED);verifyNoInteractions(authority);verify(index,never()).activateWinningPublication(any(),any());
    }
    @Test void existingWinnerDoesNotOverrideAStaleNativeFence() {
        var index=mock(ArtifactReuseIndexPort.class);var authority=mock(CompletionAuthorityPort.class);var publication=publication();
        when(index.stageWinningPublication(publication)).thenReturn(ReusePublicationResult.CONFLICT_REJECTED);
        var key=publication.record().executionReuseKey();var old=mock(ReusableArtifactRecord.class,RETURNS_DEEP_STUBS);when(old.tenantId()).thenReturn("tenant");when(old.executionReuseKey()).thenReturn(key);when(old.artifactPin().artifactId()).thenReturn(new ArtifactId("old-artifact"));when(index.lookup(anyString(),any())).thenReturn(java.util.Optional.of(old));
        when(authority.completeIfCurrent(any(),any())).thenReturn(CompletionDecision.STALE_GENERATION_REJECTED);
        var result=new FencedReuseCompletionOrchestrator(index,authority).complete(publication,mock(CompletionEvidence.class),new ArtifactCommitEvidence(new ArtifactId("current-artifact")));
        assertThat(result.completionDecision()).isEqualTo(CompletionDecision.STALE_GENERATION_REJECTED);assertThat(result.publicationResult()).isEqualTo(ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED);verify(index,never()).activateWinningPublication(any(),any());
    }
    private static ReusableArtifactPublication publication() {
        var p=mock(ReusableArtifactPublication.class,RETURNS_DEEP_STUBS);when(p.record().tenantId()).thenReturn("tenant");when(p.record().artifactPin().artifactId()).thenReturn(new ArtifactId("current-artifact"));return p;
    }
}
