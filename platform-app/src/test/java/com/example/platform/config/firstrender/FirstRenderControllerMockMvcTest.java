package com.example.platform.config.firstrender;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
class FirstRenderControllerMockMvcTest {
    private static final String REVISION="rev-1";
    private FirstRenderSubmissionService submissions;
    private MockMvc mvc;
    @BeforeEach void setup() {
        submissions=mock(FirstRenderSubmissionService.class);
        var actors=mock(CanonicalActorResolver.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("actor","tenant",Set.of(),"test")));
        mvc=mockMvc(new FirstRenderController(submissions,actors));
    }
    @Test void returnsCreatedOrReplayWithDurableIdentity() throws Exception {
        var response=new FirstRenderPlanHttpResponse("job","request","plan","PLANNED","render:tenant:job",false);
        when(submissions.submit(any(),any(),any(),eq("key")))
            .thenReturn(new FirstRenderSubmissionService.Submission(true,response))
            .thenReturn(new FirstRenderSubmissionService.Submission(false,response));
        mvc.perform(post("/api/first-render/plans").header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON).content(validJson("default")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.renderJobId").value("job")).andExpect(jsonPath("$.workflowStarted").value(false));
        mvc.perform(post("/api/first-render/plans").header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON).content(validJson("default")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.renderJobId").value("job"));
    }
    @Test
    void invalidInputsAreRejected() throws Exception {
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"\"", "0", "2000", "30", "1", "MP4", "\"p\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"rev-1\"", "0", "2000", "30", "1", "MP4", "\"\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":\"rev-1\",\"outputFormat\":\"MP4\",\"profile\":\"p\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"rev-1\"", "2000", "2000", "30", "1", "MP4", "\"p\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"rev-1\"", "0", "2000", "30", "1", "WEBM", "\"p\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/first-render/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":\"rev-1\",\"extent\":{\"startMillis\":0,\"endMillis\":2000,"
                                + "\"frameRateNumerator\":30,\"frameRateDenominator\":1},\"profile\":\"p\"}"))
                .andExpect(status().isBadRequest());
    }

    private static MockMvc mockMvc(FirstRenderController controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    private static String validJson(String profile) {
        return body("\"" + REVISION + "\"", "0", "2000", "30", "1", "MP4", "\"" + profile + "\"");
    }

    private static String body(String revisionId, String start, String end, String rateNum, String rateDen,
            String outputFormat, String profile) {
        return "{\"revisionId\":" + revisionId
                + ",\"extent\":{\"startMillis\":" + start + ",\"endMillis\":" + end
                + ",\"frameRateNumerator\":" + rateNum + ",\"frameRateDenominator\":" + rateDen + "}"
                + ",\"outputFormat\":\"" + outputFormat + "\""
                + ",\"profile\":" + profile + "}";
    }
}
