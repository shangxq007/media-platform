package com.example.platform.web.render;

import com.example.platform.operation.invocation.*;
import com.example.platform.operation.operation.*;
import com.example.platform.operation.application.TimelineMediaClipOperationService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimelineInitializeOperationControllerTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void initializationUsesTheTypedInvocationAndAbsenceBase() throws Exception {
        TenantContext.set("tenant-a");
        var authorization = mock(TimelineProjectAuthorizationService.class);
        var invocations = mock(OperationInvocationPort.class);
        var actor = CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test");
        when(authorization.requireWrite("tenant-a", "project-a")).thenReturn(actor);
        when(invocations.invoke(any(), any())).thenAnswer(call -> {
            OperationRequest request = call.getArgument(0);
            OperationInvocationContext context = call.getArgument(1);
            return new OperationInvocationResult.Applied(request.definitionId(), request.version(),
                    "a".repeat(64), request.baseRevisionId(), "revision-1", "hash-1",
                    context.invocationId(), null);
        });
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineInitializeOperationController(authorization, invocations)).build();
        mvc.perform(post("/api/tenants/tenant-a/projects/project-a/timeline-operations/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackId\":\"video-1\",\"commandId\":\"init-1\"}"))
                .andExpect(status().isCreated());
        var requests = ArgumentCaptor.forClass(OperationRequest.class);
        verify(invocations).invoke(requests.capture(), any());
        var request = requests.getValue();
        assertEquals(OperationDefinition.V3.INITIALIZE_TIMELINE.definitionId(), request.definitionId());
        assertEquals("project-a", ((OperationTargetRequest.TimelineTargetRequest) request.target()).timelineId());
        assertEquals("video-1", ((OperationParameters.InitializeTimelineParameters) request.parameters()).trackId());
        assertEquals(TimelineMediaClipOperationService.INITIAL_BASE_REVISION_ID, request.baseRevisionId());
        assertEquals(TimelineMediaClipOperationService.INITIAL_BASE_CONTENT_HASH, request.baseContentHash());
    }
}
