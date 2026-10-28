package com.example.platform.thumbnail;

import com.example.platform.config.mediaexecution.ArtifactCapabilityApplicationService;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort.*;
import com.example.platform.execution.admission.PlatformExecutionState;
import com.example.platform.artifact.app.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Thin HTTP adaptation only; actual HTTP authorization/bytes are covered by the local runtime entry. */
class ThumbnailControllerExecutionTest {
    final ArtifactCapabilityApplicationService owner=mock(ArtifactCapabilityApplicationService.class);
    final ArtifactContentRead contents=mock(ArtifactContentRead.class);
    final ThumbnailController api=new ThumbnailController(owner,contents);
    CapabilityExecution execution(PlatformExecutionState state,String artifact) {
        return new CapabilityExecution("execution-a","tenant-a","project-a",2,state,artifact,null,false,false,null);
    }
    @Test void controlCommandsPassTheExactExecutionScopeAndGenerationToTheOwner() {
        var result=execution(PlatformExecutionState.CANCELLED,null);
        when(owner.cancel("tenant-a","project-a","execution-a",2,"media.thumbnail")).thenReturn(result);
        assertEquals(result.handle(),api.cancel("tenant-a","project-a","execution-a",new ThumbnailController.Control(2)));
        verify(owner).cancel("tenant-a","project-a","execution-a",2,"media.thumbnail");
        when(owner.retry("tenant-a","project-a","execution-a",2,"media.thumbnail")).thenReturn(execution(PlatformExecutionState.RETRYING,null));
        assertEquals(PlatformExecutionState.RETRYING,api.retry("tenant-a","project-a","execution-a",new ThumbnailController.Control(2)).state());
        verify(owner).retry("tenant-a","project-a","execution-a",2,"media.thumbnail");verifyNoInteractions(contents);
    }
    @Test void pendingFailedCancelledOrMissingExecutionsNeverReadArtifactBytes() {
        for(var state:new PlatformExecutionState[]{PlatformExecutionState.ADMITTED,PlatformExecutionState.RUNNING,PlatformExecutionState.RETRYING,PlatformExecutionState.FAILED,PlatformExecutionState.CANCELLED}) {
            when(owner.find("tenant-a","project-a","execution-a","media.thumbnail")).thenReturn(Optional.of(execution(state,null)));
            var failure=assertThrows(ResponseStatusException.class,()->api.image("tenant-a","project-a","execution-a"));
            assertEquals(409,failure.getStatusCode().value());
        }
        when(owner.find("tenant-a","project-a","execution-a","media.thumbnail")).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->api.image("tenant-a","project-a","execution-a")).getStatusCode().value());
        verifyNoInteractions(contents);
    }
}
