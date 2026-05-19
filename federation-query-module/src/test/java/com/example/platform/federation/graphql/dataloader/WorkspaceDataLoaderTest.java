package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.identity.api.dto.WorkspaceResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceDataLoaderTest {

    @Test
    void loadsWorkspaceDataInBatch() throws Exception {
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        WorkspaceResponse response = new WorkspaceResponse("ws-1", "tenant-1", "Test", "desc", "FREE", "ACTIVE", Instant.now(), Instant.now());
        when(workspaceService.getWorkspace("ws-1")).thenReturn(response);

        WorkspaceDataLoader loader = new WorkspaceDataLoader(workspaceService);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("ws-1"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("ws-1"));
    }
}
