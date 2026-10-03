package com.example.platform.web.render;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.observability.monitoring.SentryMonitoringService;
import com.example.platform.render.app.operation.TextOperationService;
import com.example.platform.render.app.operation.TextOperationService.TextOperationPreview;
import com.example.platform.render.app.operation.TextOperationService.TextOperationResult;
import com.example.platform.render.app.operation.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** P2-5.5: HTTP layer for the 18 text-op endpoints (9 preview + 9 apply). */
class TextOperationControllerMockMvcTest {

    private static final String BASE = "/api/tenants/tenant-a/projects/project-1/timeline-operations/text-elements";
    private static final String HASH = "a".repeat(64);

    private static final String[] OPS = {
            "add", "remove", "replace-content", "set-style-range", "set-paragraph-style",
            "set-font-selection", "set-font-fallback-policy", "set-variable-font-axis", "set-layout"};

    private TextOperationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        service = mock(TextOperationService.class);
        CanonicalActorResolver actorResolver = mock(CanonicalActorResolver.class);
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var controller = new TextOperationController(service, actorResolver);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(Optional.<SentryMonitoringService>empty()))
                .build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void allNinePreviewEndpointsAreMapped() throws Exception {
        for (String op : OPS) {
            int code = mvc.perform(post(BASE + "/" + op + "/preview")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertNotEquals(404, code, "preview endpoint missing: " + op);
        }
    }

    @Test
    void allNineApplyEndpointsAreMapped() throws Exception {
        for (String op : OPS) {
            int code = mvc.perform(post(BASE + "/" + op + "/apply")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertNotEquals(404, code, "apply endpoint missing: " + op);
        }
    }

    @Test
    void removePreviewDelegatesToService() throws Exception {
        when(service.preview(anyString(), anyString(), any(), any())).thenReturn(
                new TextOperationPreview("TEXT_OPERATION_V1", HASH, "project-1", "rev-b",
                        HASH, HASH, false, List.of("text-remove(e1)"),
                        List.of("CANONICAL_TIMELINE_VALID")));

        mvc.perform(post(BASE + "/remove/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevisionId\":\"rev-b\",\"baseContentHash\":\"" + HASH
                                + "\",\"textElementId\":\"e1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planDigest").value(HASH))
                .andExpect(jsonPath("$.changeKeys[0]").value("text-remove(e1)"));
    }

    @Test
    void removeApplyDelegatesAndReturnsCreated() throws Exception {
        when(service.authorizeAndApply(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(new TextOperationResult("APPLIED", HASH, "rev-b", "rev-new", HASH, "rev-b"));

        mvc.perform(post(BASE + "/remove/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevisionId\":\"rev-b\",\"baseContentHash\":\"" + HASH
                                + "\",\"textElementId\":\"e1\",\"expectedPlanDigest\":\"" + HASH
                                + "\",\"applyCommandId\":\"apply-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newRevisionId").value("rev-new"));
    }

    @Test
    void applyWithoutDigestIsMalformed() throws Exception {
        mvc.perform(post(BASE + "/remove/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevisionId\":\"rev-b\",\"baseContentHash\":\"" + HASH
                                + "\",\"textElementId\":\"e1\",\"applyCommandId\":\"apply-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void typedFailureMapsToStatus() throws Exception {
        when(service.preview(anyString(), anyString(), any(), any()))
                .thenThrow(new TimelineOperationException(
                        TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH, List.of("mismatch")));

        mvc.perform(post(BASE + "/remove/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevisionId\":\"rev-b\",\"baseContentHash\":\"" + HASH
                                + "\",\"textElementId\":\"e1\"}"))
                .andExpect(status().isForbidden());
    }
}
