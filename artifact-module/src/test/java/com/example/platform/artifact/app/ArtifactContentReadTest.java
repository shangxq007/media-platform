package com.example.platform.artifact.app;

import com.example.platform.artifact.infrastructure.ArtifactContentReadService;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArtifactContentReadTest {
    final ArtifactScope scope=new ArtifactScope("tenant-a","project-a","job-a");
    final ArtifactId id=new ArtifactId("output-a");
    final byte[] bytes=java.util.HexFormat.of().parseHex("000000186674797069736f6d0000020069736f6d6d703432");
    final ContentDigest digest=hash(bytes);
    final ArtifactApplicationQuery scoped=mock(ArtifactApplicationQuery.class);
    final ArtifactQueryService query=mock(ArtifactQueryService.class);
    final VerifiedStorageRead storage=mock(VerifiedStorageRead.class);
    final ArtifactContentReadService service=new ArtifactContentReadService(scoped,query,storage);
    Artifact artifact(ArtifactState state){return new Artifact(id,"tenant-a",digest,bytes.length,ArtifactMediaType.VIDEO,ArtifactKind.RENDER_MASTER,state,1,Instant.EPOCH);}
    @BeforeEach void setup(){TenantContext.set("tenant-a");}
    @AfterEach void clear(){TenantContext.clear();}
    void ready() {
        when(scoped.findArtifact(scope,id)).thenReturn(Optional.of(artifact(ArtifactState.AVAILABLE)));
        when(query.listReplicas("tenant-a",id)).thenReturn(List.of(new ArtifactReplicaBinding("binding",id,new StorageObjectId("object-a"),new StorageReplicaId("replica-a"),new StorageProviderId("local"),ReplicaRole.PRIMARY,"local",Instant.EPOCH)));
    }
    @Test void exactLogicalScopeReadsVerifiedBytesAndReturnsNoStorageCoordinates() {
        ready();when(storage.read(any(),any(),eq(digest),eq((long)bytes.length),anyLong())).thenReturn(bytes);
        var result=service.read(scope,id);
        assertArrayEquals(bytes,result.bytes());assertEquals("video/mp4",result.contentType());assertEquals("output.mp4",result.fileName());
        byte[] copy=result.bytes();copy[0]=99;assertArrayEquals(bytes,result.bytes());
        verify(scoped).findArtifact(scope,id);verify(storage).read(new StorageProviderId("local"),new StorageObjectId("object-a"),digest,bytes.length,64L*1024*1024);
    }
    @Test void otherTenantCannotQueryOrReadStorage(){TenantContext.set("other");assertThrows(RuntimeException.class,()->service.read(scope,id));verifyNoInteractions(scoped,query,storage);}
    @Test void otherProjectOrJobIsNotFoundBeforeStorage(){assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(new ArtifactScope("tenant-a","other-project","job-a"),id));verifyNoInteractions(query,storage);}
    @Test void allNonAvailableStatesFailBeforeReplicaLookup(){for(var state:ArtifactState.values())if(state!=ArtifactState.AVAILABLE){when(scoped.findArtifact(scope,id)).thenReturn(Optional.of(artifact(state)));assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(scope,id));}verifyNoInteractions(query,storage);}
    @Test void corruptMissingOrEscapingPhysicalObjectHasOneRedactedFailure(){ready();when(storage.read(any(),any(),any(),anyLong(),anyLong())).thenThrow(new IllegalStateException("private/path/secret"));var e=assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(scope,id));assertEquals("Artifact content unavailable",e.getMessage());assertFalse(e.getMessage().contains("private"));}
    @Test void wrongLengthOrDigestIsNotDisclosedEvenIfStorageClaimsVerification() {
        ready();
        byte[] altered=bytes.clone();altered[altered.length-1]^=1;
        when(storage.read(any(),any(),any(),anyLong(),anyLong())).thenReturn(altered);
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(scope,id));
        when(storage.read(any(),any(),any(),anyLong(),anyLong())).thenReturn(Arrays.copyOf(bytes,bytes.length-1));
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(scope,id));
    }
    private static ContentDigest hash(byte[] bytes) {
        try { return ContentDigest.sha256(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
    }
    @Test void verifiedBytesWithWrongContainerSignatureAreNotDisclosed(){ready();when(storage.read(any(),any(),any(),anyLong(),anyLong())).thenReturn(new byte[24]);assertThrows(ArtifactApplicationService.ArtifactAccessException.class,()->service.read(scope,id));}
}
