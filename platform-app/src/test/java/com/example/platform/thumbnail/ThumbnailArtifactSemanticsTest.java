package com.example.platform.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.artifact.domain.*;
import com.example.platform.contract.media.ThumbnailContracts;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.*;
import com.example.platform.storage.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

/** Drives the production read use case; HTTP/native persistence are separately exercised end to end. */
class ThumbnailArtifactSemanticsTest {
    final ThumbnailTaskStore tasks=mock(ThumbnailTaskStore.class);
    final ArtifactQueryService artifacts=mock(ArtifactQueryService.class);
    final StoragePlacementQuery storage=mock(StoragePlacementQuery.class);
    final ArtifactProjectAuthorizationPort authorization=mock(ArtifactProjectAuthorizationPort.class);
    final ThumbnailArtifactReadService service=new ThumbnailArtifactReadService(tasks,artifacts,storage,authorization);
    final ArtifactId id=new ArtifactId("image");final byte[] bytes={(byte)0xff,(byte)0xd8};
    final StorageObjectId object=new StorageObjectId("object");final StorageReplicaId replica=new StorageReplicaId("replica");
    final StorageOwnershipScope scope=new StorageOwnershipScope("tenant","project");
    Artifact artifact(ArtifactKind kind) throws Exception {
        var digest=ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        return new Artifact(id,"tenant",digest,bytes.length,ArtifactMediaType.IMAGE,kind,ArtifactState.AVAILABLE,1,Instant.EPOCH);
    }
    @BeforeEach void prepare() throws Exception {
        TenantContext.set("tenant");
        when(tasks.find("tenant","project","task")).thenReturn(Optional.of(new ThumbnailContracts.Result("task",ThumbnailContracts.Status.COMPLETED,"image",null)));
        when(artifacts.getArtifact("tenant",id)).thenReturn(Optional.of(artifact(ArtifactKind.DERIVED_MEDIA)));
        when(artifacts.getDirectProvenance("tenant",id)).thenReturn(List.of(new ProvenanceEdge("edge","tenant",new ArtifactId("source"),id,ProvenanceRelationType.THUMBNAIL_OF,"thumbnail",1,"task","a","b",Instant.EPOCH)));
        when(artifacts.listReplicas("tenant",id)).thenReturn(List.of(new ArtifactReplicaBinding("binding",id,object,replica,new StorageProviderId("local"),ReplicaRole.PRIMARY,"local",Instant.EPOCH)));
        when(storage.read(scope,object,replica)).thenReturn(bytes);
        when(storage.reference(scope,object,replica)).thenReturn(new StorageReference("ref","LOCAL",StorageClass.STANDARD,"/internal","opaque","digest","digest",2,"image/jpeg",Instant.EPOCH,Instant.EPOCH));
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void currentProvenanceBackedThumbnailReturnsVerifiedBytes() {
        var image=service.read("tenant","project","task");assertArrayEquals(bytes,image.bytes());assertEquals("image/jpeg",image.contentType());assertEquals("image",image.artifactId());
        byte[] copy=image.bytes();copy[0]=0;assertArrayEquals(bytes,image.bytes());
        verify(authorization).requireRead("tenant","project");verify(storage).read(scope,object,replica);
    }
    @Test void legacyKindNeverBypassesProvenance() throws Exception {
        when(artifacts.getArtifact("tenant",id)).thenReturn(Optional.of(artifact(ArtifactKind.THUMBNAIL)));
        assertThrows(IllegalStateException.class,()->service.read("tenant","project","task"));verifyNoInteractions(storage);
    }
    @Test void absentThumbnailRelationNeverReadsStorage() {
        when(artifacts.getDirectProvenance("tenant",id)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.read("tenant","project","task"));verifyNoInteractions(storage);
    }
    @Test void corruptBytesFailDigestAndLengthValidation() {
        when(storage.read(scope,object,replica)).thenReturn(new byte[]{0});
        assertThrows(IllegalStateException.class,()->service.read("tenant","project","task"));verify(storage,never()).reference(any(),any(),any());
    }
    @Test void deniedProjectNeverQueriesTaskArtifactOrStorage() {
        doThrow(new SecurityException("denied")).when(authorization).requireRead("tenant","project");
        assertThrows(SecurityException.class,()->service.read("tenant","project","task"));verifyNoInteractions(tasks,artifacts,storage);
    }
    @Test void incompleteTaskNeverQueriesArtifactOrStorage() {
        when(tasks.find("tenant","project","task")).thenReturn(Optional.of(new ThumbnailContracts.Result("task",ThumbnailContracts.Status.RUNNING,null,null)));
        assertThrows(IllegalStateException.class,()->service.read("tenant","project","task"));verifyNoInteractions(artifacts,storage);
    }
}
