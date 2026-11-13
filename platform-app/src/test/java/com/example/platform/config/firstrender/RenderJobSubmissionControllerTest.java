package com.example.platform.config.firstrender;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class RenderJobSubmissionControllerTest {
    @Test void formalProductRouteUsesSharedRevisionSubmissionAndPreservesReplayIdentity() throws Exception {
        var submissions = mock(RevisionRenderSubmissionService.class);
        var actors = mock(CanonicalActorResolver.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("actor", "tenant", Set.of(), "test")));
        var result = new FirstRenderPlanHttpResponse("job", "request", "plan", "PLANNED", "render:tenant:job", true);
        when(submissions.submit(any(), any(), any(), eq("intent-1")))
                .thenReturn(new RevisionRenderSubmissionService.Submission(true, result))
                .thenReturn(new RevisionRenderSubmissionService.Submission(false, result));
        var validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        var mvc = MockMvcBuilders.standaloneSetup(new RenderJobSubmissionController(submissions, actors))
                .setValidator(validator).build();
        String body = """
                {"revisionId":"revision-1","extent":{"startMillis":0,"endMillis":2000,
                  "frameRateNumerator":24,"frameRateDenominator":1},"outputFormat":"MP4","profile":"default"}
                """;
        mvc.perform(post("/api/render-jobs").header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.renderJobId").value("job"));
        mvc.perform(post("/api/render-jobs").header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.renderJobId").value("job"));
        mvc.perform(post("/api/render-jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revisionId\":\"\",\"profile\":\"default\"}"))
                .andExpect(status().isBadRequest());
        verify(submissions, times(2)).submit(any(), any(), any(), eq("intent-1"));
    }
}
