package com.example.platform.web.render;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.observability.monitoring.SentryMonitoringService;
import com.example.platform.render.app.operation.TimelineMediaClipOperationService;
import com.example.platform.render.app.operation.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TimelineMediaClipOperationControllerMockMvcTest {

    private static final String APPLY_JSON = """
            {
              "request": {
                "baseRevisionId": "revision-R0",
                "baseContentHash": "%s",
                "trackId": "video-1",
                "clipId": "clip-1",
                "mediaAssetId": "media-1",
                "mediaStreamId": "stream-1",
                "artifactId": "artifact-1",
                "contentDigest": "%s",
                "sourceStart": "0/1",
                "sourceEnd": "10/1",
                "timelineStart": "0/1",
                "timelineEnd": "10/1",
                "rateNumerator": 1,
                "rateDenominator": 1,
                "direction": "FORWARD"
              },
              "expectedPlanDigest": "%s",
              "applyCommandId": "apply-1"
            }
            """.formatted("a".repeat(64), "b".repeat(64), "c".repeat(64));

    private TimelineMediaClipOperationService service;
    private CanonicalActorResolver actorResolver;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        service = mock(TimelineMediaClipOperationService.class);
        actorResolver = mock(CanonicalActorResolver.class);
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var controller = new TimelineMediaClipOperationController(service, actorResolver);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(
                        mock(ErrorCodeRegistry.class), Optional.<SentryMonitoringService>empty()))
                .build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    static Stream<Arguments> typedFailures() {
        return Stream.of(
                Arguments.of(TimelineOperationException.Code.STALE_TARGET_REF, 409),
                Arguments.of(TimelineOperationException.Code.IDEMPOTENCY_KEY_CONFLICT, 409),
                Arguments.of(TimelineOperationException.Code.AUTHORIZATION_DENIED, 403),
                Arguments.of(TimelineOperationException.Code.CANDIDATE_INVALID, 422),
                Arguments.of(TimelineOperationException.Code.SOURCE_REFERENCE_INVALID, 422),
                Arguments.of(TimelineOperationException.Code.PERSISTENCE_FAILURE, 500));
    }

    @ParameterizedTest
    @MethodSource("typedFailures")
    void applyMapsTypedFailureCategory(
            TimelineOperationException.Code code, int expectedStatus) throws Exception {
        when(service.authorizeAndApply(
                anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new TimelineOperationException(code, List.of("typed failure")));

        mvc.perform(post("/api/tenants/tenant-a/projects/project-a/timeline-operations/add-media-clip/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_JSON))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(code.name()));
    }

    @Test
    void malformedTransportIs400() throws Exception {
        mvc.perform(post("/api/tenants/tenant-a/projects/project-a/timeline-operations/add-media-clip/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_JSON.replace("a".repeat(64), "not-a-digest")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_INPUT"));
    }

    @Test
    void missingAuthenticatedActorIs401() throws Exception {
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.empty());

        mvc.perform(post("/api/tenants/tenant-a/projects/project-a/timeline-operations/add-media-clip/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownFailureRemains5xx() throws Exception {
        when(service.authorizeAndApply(
                anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        mvc.perform(post("/api/tenants/tenant-a/projects/project-a/timeline-operations/add-media-clip/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_JSON))
                .andExpect(status().is5xxServerError());
    }
}
