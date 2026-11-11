package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtifactOutputReadServiceTest {
    final ArtifactApplicationQuery scoped=mock(ArtifactApplicationQuery.class);
    final ArtifactContentRead content=mock(ArtifactContentRead.class);
    final AcceptedRenderOutputQuery authority=mock(AcceptedRenderOutputQuery.class);
    final ArtifactScope scope=new ArtifactScope("tenant","project","job");
    final ArtifactId id=new ArtifactId("artifact");
    final ArtifactOutputReadService service=new ArtifactOutputReadService(scoped,List.of(authority),content);
    @BeforeEach void tenant(){TenantContext.set("tenant");}
    @AfterEach void clear(){TenantContext.clear();}
    @Test void noFormalAcceptanceCannotFallBackToAnOutputReceipt() {
        when(authority.find(scope)).thenReturn(Optional.empty());assertTrue(service.find(scope).isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.read(new ArtifactOutputReference(scope,id)));verifyNoInteractions(scoped,content);
    }
    @Test void conflictingAuthoritiesRejectRatherThanSelectFirstOrLatest() {
        var other=mock(AcceptedRenderOutputQuery.class);when(authority.find(scope)).thenReturn(Optional.of(id));when(other.find(scope)).thenReturn(Optional.of(new ArtifactId("other")));
        assertThrows(IllegalStateException.class,()->new ArtifactOutputReadService(scoped,List.of(authority,other),content).find(scope));verifyNoInteractions(scoped,content);
    }
    @Test void arbitraryArtifactCannotReplaceAcceptedFinalOutput() {
        when(authority.find(scope)).thenReturn(Optional.of(id));assertThrows(IllegalArgumentException.class,()->service.read(new ArtifactOutputReference(scope,new ArtifactId("other"))));verifyNoInteractions(scoped,content);
    }
    @Test void outputBytesComeFromTheVerifyingArtifactOwner() {
        var digest=ContentDigest.sha256("a".repeat(64));when(authority.find(scope)).thenReturn(Optional.of(id));
        when(scoped.findArtifact(scope,id)).thenReturn(Optional.of(new Artifact(id,"tenant",digest,4,ArtifactMediaType.VIDEO,ArtifactKind.RENDER_MASTER,ArtifactState.AVAILABLE,1,Instant.EPOCH)));
        when(content.read(scope,id)).thenReturn(new ArtifactContentRead.Content(new byte[]{1,2,3,4},"video/mp4","output.mp4",digest));
        assertEquals(new ArtifactOutputReference(scope,id),service.find(scope).orElseThrow());assertArrayEquals(new byte[]{1,2,3,4},service.read(new ArtifactOutputReference(scope,id)).bytes());verify(content).read(scope,id);
    }
    @Test void crossTenantReferenceRejectsBeforeAnyOwnerQuery() {
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->service.find(scope));assertThrows(RuntimeException.class,()->service.read(new ArtifactOutputReference(scope,id)));verifyNoInteractions(authority,scoped,content);
    }
}
