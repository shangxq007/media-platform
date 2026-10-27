package com.example.platform.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.contract.media.*;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.*;
import com.example.platform.storage.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/** Routing contract only; real media and HTTP are covered by the owned local validation. */
class ThumbnailSourceReadTest {
    @TempDir Path root;
    @Test void failedNativeReadCannotFallBackToReceiptProtocol() throws Exception {run(true,true);}
    @Test void registeredNativeSourceDoesNotRequireAnUnrelatedLegacyReceipt() throws Exception {run(true,false);}
    @Test void activeReceiptProviderStillUsesItsOwnScopedProtocol() throws Exception {run(false,false);}
    @Test void mismatchedTaskScopeCannotChangeStatusOrReadAnySource() {
        var tasks=mock(ThumbnailTaskStore.class);var query=mock(ArtifactQueryService.class);var catalog=mock(ArtifactCatalogService.class);
        var receipts=mock(StoragePlacementQuery.class);var nativeRead=mock(VerifiedStorageRead.class);var commits=mock(ThumbnailCommitService.class);var provider=mock(FrameExtractExecutionAdapter.class);
        var service=new ThumbnailActivitiesImpl(tasks,query,catalog,receipts,nativeRead,commits,provider,root.toString());
        assertThrows(IllegalArgumentException.class,()->service.extractAndCommit("other-task","tenant","wrong-project"));
        verify(tasks).request("tenant","wrong-project","other-task");
        verifyNoMoreInteractions(tasks);verifyNoInteractions(query,catalog,receipts,nativeRead,commits,provider);
    }
    void run(boolean registered,boolean fail) throws Exception {
        var tasks=mock(ThumbnailTaskStore.class);var query=mock(ArtifactQueryService.class);var catalog=mock(ArtifactCatalogService.class);
        var receipts=mock(StoragePlacementQuery.class);var nativeRead=mock(VerifiedStorageRead.class);var commits=mock(ThumbnailCommitService.class);var provider=mock(FrameExtractExecutionAdapter.class);
        var request=new ThumbnailContracts.Request("tenant","project","source",.25,"jpeg",160,80,"source-read");
        var id=new ArtifactId("source");var pid=new StorageProviderId("provider");var object=new StorageObjectId("object");var replica=new StorageReplicaId("replica");byte[] bytes={1,2,3};
        var digest=ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        when(tasks.statusIfActive("task",ThumbnailContracts.Status.RUNNING,null,null)).thenReturn(true);when(tasks.request("tenant","project","task")).thenReturn(Optional.of(request));
        when(catalog.findArtifact("tenant","project","source")).thenReturn(Optional.of(mock(com.example.platform.artifact.domain.ArtifactCatalogEntry.class)));
        when(query.getArtifact("tenant",id)).thenReturn(Optional.of(new Artifact(id,"tenant",digest,3,ArtifactMediaType.VIDEO,ArtifactKind.SOURCE_MEDIA,ArtifactState.AVAILABLE,1,Instant.EPOCH)));
        when(query.listReplicas("tenant",id)).thenReturn(List.of(new ArtifactReplicaBinding("b",id,object,replica,pid,ReplicaRole.PRIMARY,"local",Instant.EPOCH)));
        when(nativeRead.supports(pid)).thenReturn(registered);
        if(fail)when(nativeRead.read(pid,object,digest,3,512L*1024*1024)).thenThrow(new IllegalStateException("integrity mismatch"));
        else when(nativeRead.read(pid,object,digest,3,512L*1024*1024)).thenReturn(bytes);
        when(receipts.read(new StorageOwnershipScope("tenant","project"),object,replica)).thenReturn(bytes);
        when(provider.invoke(eq(request),any(byte[].class),any())).thenReturn(FrameExtractResult.failure("CONTROLLED_STOP"));
        var service=new ThumbnailActivitiesImpl(tasks,query,catalog,receipts,nativeRead,commits,provider,root.toString());
        if(fail)assertThrows(IllegalArgumentException.class,()->service.extractAndCommit("task","tenant","project"));
        else assertThrows(IllegalArgumentException.class,()->service.extractAndCommit("task","tenant","project"));
        if(registered)verifyNoInteractions(receipts);else verify(receipts).read(new StorageOwnershipScope("tenant","project"),object,replica);
        if(fail)verifyNoInteractions(provider);else verify(provider).invoke(eq(request),aryEq(bytes),any());
        verifyNoInteractions(commits);
    }
}
