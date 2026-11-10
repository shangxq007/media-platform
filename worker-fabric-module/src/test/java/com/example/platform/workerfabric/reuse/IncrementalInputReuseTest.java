package com.example.platform.workerfabric.reuse;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.*;
import com.example.platform.execution.taskgraph.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncrementalInputReuseTest {
    final String tenant="tenant",project="project";
    final ExecutionReuseKey key=unitKey();
    static ExecutionReuseKey unitKey() {
        String canonical="roadmap22.execution-reuse-key.v1/unit-only-semantic-key";
        try {return new ExecutionReuseKey(ExecutionReuseKey.VERSION,canonical,java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    final ExecutableTaskId task=new ExecutableTaskId("a".repeat(64));
    final ArtifactId id=new ArtifactId("derived");
    final ContentDigest digest=ContentDigest.sha256("b".repeat(64));
    final ArtifactReuseIndexPort index=mock(ArtifactReuseIndexPort.class);
    final ArtifactQueryService artifacts=mock(ArtifactQueryService.class);
    final ArtifactApplicationQuery scoped=mock(ArtifactApplicationQuery.class);
    Artifact artifact(ArtifactKind kind,ArtifactState state) {
        return new Artifact(id,tenant,digest,10,ArtifactMediaType.VIDEO,kind,state,1,Instant.EPOCH);
    }
    ArtifactReuseResolver ready(ArtifactKind kind) {
        var value=artifact(kind,ArtifactState.AVAILABLE);
        when(index.lookup(tenant,key)).thenReturn(Optional.of(new ReusableArtifactRecord(tenant,key,new ArtifactPin(id,digest),task,new ExecutionAttemptId("old-attempt"),new ExecutionOwnershipGeneration(1),Instant.EPOCH)));
        when(artifacts.getArtifact(tenant,id)).thenReturn(Optional.of(value));
        when(scoped.findProjectArtifact(new ArtifactProjectScope(tenant,project),id)).thenReturn(Optional.of(value));
        return new ArtifactReuseResolver(index,artifacts,scoped);
    }
    @Test void committedSameProjectDerivedMediaIsInputOnly() {
        assertEquals(ValidatedReuseDecision.Outcome.VALIDATED_HIT,ready(ArtifactKind.DERIVED_MEDIA).resolveIntermediate(tenant,project,key,Cacheability.CACHEABLE,task).outcome());
    }
    @Test void projectBoundaryCannotBeInferredFromTenantHit() {
        assertEquals(ValidatedReuseDecision.Outcome.UNAUTHORIZED,ready(ArtifactKind.DERIVED_MEDIA).resolveIntermediate(tenant,"other",key,Cacheability.CACHEABLE,task).outcome());
    }
    @Test void finalAndSourceArtifactsCannotBeBorrowedAsIntermediates() {
        for(var kind:List.of(ArtifactKind.RENDER_MASTER,ArtifactKind.SOURCE_MEDIA))
            assertEquals(ValidatedReuseDecision.Outcome.UNAUTHORIZED,ready(kind).resolveIntermediate(tenant,project,key,Cacheability.CACHEABLE,task).outcome());
    }
    @Test void cacheabilityAndExactTaskRemainMandatory() {
        var resolver=ready(ArtifactKind.DERIVED_MEDIA);
        assertEquals(ValidatedReuseDecision.Outcome.NOT_CACHEABLE,resolver.resolveIntermediate(tenant,project,key,Cacheability.NOT_CACHEABLE,task).outcome());
        assertEquals(ValidatedReuseDecision.Outcome.UNAUTHORIZED,resolver.resolveIntermediate(tenant,project,key,Cacheability.CACHEABLE,new ExecutableTaskId("c".repeat(64))).outcome());
    }
}
