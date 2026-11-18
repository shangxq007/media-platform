package com.example.platform.web.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimelineClipEditOperationControllerTest {
    private static final String PATH =
            "/api/tenants/tenant-a/projects/project-a/timeline-operations/clips/apply";
    private static final String HASH = "a".repeat(64);

    @AfterEach void clearTenant() { TenantContext.clear(); }

    private static final String PREVIEW_PATH =
            "/api/tenants/tenant-a/projects/project-a/timeline-operations/clips/preview";

    private static String trimBody(String edge, String delta) {
        return """
                {"type":"TRIM_CLIP","baseRevisionId":"rev-1","baseContentHash":"%s",
                 "clipId":"clip-1","delta":"%s","edge":"%s","commandId":"preview-1"}
                """.formatted(HASH, delta, edge);
    }

    @Test
    void trimPreviewValidatesThroughTheCanonicalAuthorityWithoutPersisting() throws Exception {
        TenantContext.set("tenant-a");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();

        mvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(trimBody("START", "1/2")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("VALID"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.edge").value("START"));

        var requests = ArgumentCaptor.forClass(OperationRequest.class);
        verify(invocations).validate(requests.capture(), any(), org.mockito.ArgumentMatchers.eq("project-a"));
        verify(invocations, org.mockito.Mockito.never()).invoke(any(), any());
        assertEquals(OperationDefinition.V1.TRIM.definitionId(), requests.getValue().definitionId());
        var trim = assertInstanceOf(OperationParameters.TrimParameters.class, requests.getValue().parameters());
        assertEquals(OperationParameters.TrimParameters.TrimEdge.START, trim.edge());
    }

    @Test
    void splitPreviewValidatesRightIdentityAndSplitTimeWithoutPersisting() throws Exception {
        TenantContext.set("tenant-a");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();
        String body = """
                {"type":"SPLIT_CLIP","baseRevisionId":"rev-1","baseContentHash":"%s",
                 "clipId":"clip-1","splitTime":"2/1","rightClipId":"clip-right",
                 "commandId":"split-preview-1"}
                """.formatted(HASH);

        mvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("VALID"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.splitTime").value("2/1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.rightClipId").value("clip-right"));

        var requests = ArgumentCaptor.forClass(OperationRequest.class);
        verify(invocations).validate(requests.capture(), any(), org.mockito.ArgumentMatchers.eq("project-a"));
        verify(invocations, org.mockito.Mockito.never()).invoke(any(), any());
        assertEquals(OperationDefinition.V3.SPLIT_CLIP.definitionId(), requests.getValue().definitionId());
        var split = assertInstanceOf(OperationParameters.SplitParameters.class, requests.getValue().parameters());
        assertEquals("clip-right", split.rightClipId().value());
        assertEquals("2/1", split.splitTimelineTime().toString());
    }

    @Test
    void trimPreviewSurfacesTheCanonicalRejection() throws Exception {
        TenantContext.set("tenant-a");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("trim must leave a positive media range"))
                .when(invocations).validate(any(), any(), any());
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();

        mvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(trimBody("END", "1/2")))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorCode").value("INVALID_REQUEST"));
        verify(invocations, org.mockito.Mockito.never()).invoke(any(), any());
    }


    @Test
    void eachEditIsSubmittedAsExactProjectScopedOperation() throws Exception {
        TenantContext.set("tenant-a");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        when(invocations.invoke(any(), any())).thenAnswer(call -> {
            OperationRequest request = call.getArgument(0);
            OperationInvocationContext context = call.getArgument(1);
            return new OperationInvocationResult.Applied(
                    request.definitionId(), request.version(), "b".repeat(64),
                    request.baseRevisionId(), "rev-2", "c".repeat(64),
                    context.invocationId(), null);
        });
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();

        String[] types = {"MOVE_CLIP", "TRIM_CLIP", "DELETE_CLIP"};
        for (String type : types) {
            String parameters = switch (type) {
                case "MOVE_CLIP" -> ",\"delta\":\"1/2\",\"absolute\":false";
                case "TRIM_CLIP" -> ",\"delta\":\"1/2\",\"edge\":\"START\"";
                default -> "";
            };
            String body = """
                    {"type":"%s","baseRevisionId":"rev-1","baseContentHash":"%s",
                     "clipId":"clip-1","commandId":"cmd-1"%s}
                    """.formatted(type, HASH, parameters);
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        var requests = ArgumentCaptor.forClass(OperationRequest.class);
        var contexts = ArgumentCaptor.forClass(OperationInvocationContext.class);
        verify(invocations, org.mockito.Mockito.times(3)).invoke(requests.capture(), contexts.capture());
        assertEquals(OperationDefinition.V1.MOVE.definitionId(), requests.getAllValues().get(0).definitionId());
        assertEquals(OperationDefinition.V1.TRIM.definitionId(), requests.getAllValues().get(1).definitionId());
        assertEquals(OperationDefinition.V1.DELETE.definitionId(), requests.getAllValues().get(2).definitionId());
        assertInstanceOf(OperationParameters.MoveParameters.class, requests.getAllValues().get(0).parameters());
        assertInstanceOf(OperationParameters.TrimParameters.class, requests.getAllValues().get(1).parameters());
        assertInstanceOf(OperationParameters.NoParameters.class, requests.getAllValues().get(2).parameters());
        for (OperationRequest request : requests.getAllValues()) {
            var target = assertInstanceOf(OperationTargetRequest.ClipSelectionTargetRequest.class, request.target());
            assertEquals("project-a", target.timelineId());
            assertEquals("rev-1", request.baseRevisionId());
            assertEquals(HASH, request.baseContentHash());
        }
        assertTrue(contexts.getAllValues().stream().allMatch(
                context -> context.actor().tenantId().equals("tenant-a")));
    }

    @Test
    void ambientTenantMismatchRejectsBeforeInvocation() throws Exception {
        TenantContext.set("tenant-b");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(
                """
                {"type":"DELETE_CLIP","baseRevisionId":"rev-1","baseContentHash":"%s",
                 "clipId":"clip-1","commandId":"cmd-1"}
                """.formatted(HASH))).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(invocations);
    }

    @Test
    void base64TimelineHashAndIntegerMoveReachTypedInvocation() throws Exception {
        TenantContext.set("tenant-a");
        var actors = mock(CanonicalActorResolver.class);
        var invocations = mock(OperationInvocationPort.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        when(invocations.invoke(any(), any())).thenAnswer(call -> {
            OperationRequest request = call.getArgument(0);
            OperationInvocationContext context = call.getArgument(1);
            return new OperationInvocationResult.Applied(request.definitionId(), request.version(),
                    "b".repeat(64), request.baseRevisionId(), "rev-2", "c".repeat(64),
                    context.invocationId(), null);
        });
        var mvc = MockMvcBuilders.standaloneSetup(
                new TimelineClipEditOperationController(invocations, actors)).build();
        String hash = "Ab".repeat(21) + "C=";
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"MOVE_CLIP","baseRevisionId":"rev-1","baseContentHash":"%s",
                 "clipId":"clip-1","delta":"2","absolute":true,"commandId":"cmd-1"}
                """.formatted(hash))).andExpect(status().isCreated());
        var requests = ArgumentCaptor.forClass(OperationRequest.class);
        verify(invocations).invoke(requests.capture(), any());
        assertEquals(hash, requests.getValue().baseContentHash());
        var parameters = assertInstanceOf(OperationParameters.MoveParameters.class,
                requests.getValue().parameters());
        assertEquals(com.example.platform.shared.time.MediaTime.ofTicks(2, 1), parameters.delta());
    }
}
