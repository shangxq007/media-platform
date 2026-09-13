package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.api.workspace.WorkspaceQueries;
import com.example.platform.identity.api.workspace.WorkspaceResponse;
import com.example.platform.shared.web.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceDataLoaderTest {
    @Test void loadsCanonicalFieldsOnTheRequestThread() {
        WorkspaceQueries owner=mock(WorkspaceQueries.class);
        Thread requestThread=Thread.currentThread();
        when(owner.getWorkspace("ws")).thenAnswer(invocation->{
            assertSame(requestThread,Thread.currentThread());
            return new WorkspaceResponse("ws","tenant","Name",null,"FREE","ACTIVE",Instant.EPOCH,Instant.EPOCH);
        });
        var result=new WorkspaceDataLoader(owner).load(Set.of("ws")).toCompletableFuture().join();
        assertEquals("Name",result.get("ws").get("name"));assertEquals("tenant",result.get("ws").get("tenantId"));
        assertEquals("ws",result.get("ws").get("id"));assertFalse(result.get("ws").containsKey("value"));
    }
    @Test void authorizationAndMissingResourcesFailWithoutAnIdOnlyFallback() {
        for(var code:java.util.List.of(CommonErrorCode.INSUFFICIENT_PERMISSION,CommonErrorCode.RESOURCE_NOT_FOUND)){
            WorkspaceQueries owner=mock(WorkspaceQueries.class);var failure=new PlatformException(code,"Unavailable");
            when(owner.getWorkspace("ws")).thenThrow(failure);
            var pending=new WorkspaceDataLoader(owner).load(Set.of("ws")).toCompletableFuture();
            assertSame(failure,assertThrows(CompletionException.class,pending::join).getCause());
        }
    }
}
