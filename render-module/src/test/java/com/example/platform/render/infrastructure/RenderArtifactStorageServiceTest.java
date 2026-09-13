package com.example.platform.render.infrastructure;
import com.example.platform.artifact.app.*;
import com.example.platform.storage.api.*;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RenderArtifactStorageServiceTest {
 @AfterEach void clear(){TenantContext.clear();}
 @Test void unsupportedMediaRejectsBeforeWrite(){
   TenantContext.set("tenant"); var storage=mock(StorageOutputPort.class); var artifacts=mock(ArtifactOutputCommit.class);
   assertThrows(IllegalArgumentException.class,()->new RenderArtifactStorageService(storage,artifacts).uploadJobOutput("job","project","file","application/unknown"));
   verifyNoInteractions(storage,artifacts);
 }
 @Test void failedWriteCannotCommitArtifact(){
   TenantContext.set("tenant"); var storage=mock(StorageOutputPort.class); var artifacts=mock(ArtifactOutputCommit.class);
   when(storage.write(any())).thenThrow(new IllegalStateException("incomplete write"));
   assertThrows(IllegalStateException.class,()->new RenderArtifactStorageService(storage,artifacts).uploadJobOutput("job","project","file.mp4","video/mp4"));
   verifyNoInteractions(artifacts);
 }
 @Test void missingTenantCannotWrite(){
   var storage=mock(StorageOutputPort.class); var artifacts=mock(ArtifactOutputCommit.class);
   assertThrows(RuntimeException.class,()->new RenderArtifactStorageService(storage,artifacts).uploadJobOutput("job","project","file.mp4","video/mp4"));
   verifyNoInteractions(storage,artifacts);
 }
}
