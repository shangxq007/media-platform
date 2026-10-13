package com.example.platform.config.firstrender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** Phase 4/6 acceptance for the First Render HTTP entry (full chain: Phase 5 + Phase 6). */
class FirstRenderControllerMockMvcTest {

    private static final String TENANT = "tenant-a";
    private static final String REVISION = "rev-1";

    private FirstRenderPlanningAndBindingService planningAndBindingService;
    private FirstRenderSaveAndStartService saveAndStartService;
    private CanonicalActorResolver actorResolver;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        planningAndBindingService = mock(FirstRenderPlanningAndBindingService.class);
        saveAndStartService = mock(FirstRenderSaveAndStartService.class);
        actorResolver = mock(CanonicalActorResolver.class);
        when(actorResolver.resolveCurrentActor())
                .thenReturn(Optional.of(CanonicalActor.user("actor-a", TENANT, Set.of(), "test")));
        mvc = mockMvc(new FirstRenderController(
                planningAndBindingService, saveAndStartService, actorResolver));
    }

    @Test
    void createsPlanAndReturnsSixFields() throws Exception {
        // executionPlanId is taken from the physical plan id; status stays "PLANNED".
        FirstRenderPlanningAndBindingResult planning = result("rj_abc", "req-xyz", "ep-123");
        when(planningAndBindingService.planAndBind(any())).thenReturn(planning);
        when(saveAndStartService.start(any()))
                .thenReturn(new FirstRenderStartResult("render:" + TENANT + ":rj_abc", true));

        mvc.perform(post("/api/first-render/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("prof-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.renderJobId").value("rj_abc"))
                .andExpect(jsonPath("$.requestId").value("req-xyz"))
                .andExpect(jsonPath("$.executionPlanId").value("ep-123"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.workflowId").value("render:" + TENANT + ":rj_abc"))
                .andExpect(jsonPath("$.workflowStarted").value(true));
    }

    @Test
    void invokesPhase5AndPhase6ExactlyOnce() throws Exception {
        FirstRenderPlanningAndBindingResult planning = result("rj_abc", "req-xyz", "ep-123");
        when(planningAndBindingService.planAndBind(any())).thenReturn(planning);
        when(saveAndStartService.start(any()))
                .thenReturn(new FirstRenderStartResult("render:" + TENANT + ":rj_abc", true));

        mvc.perform(post("/api/first-render/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("prof-1")))
                .andExpect(status().isCreated());

        verify(planningAndBindingService, times(1)).planAndBind(any());
        verify(saveAndStartService, times(1)).start(any());
    }

    @Test
    void mapsRequestAndTakesInitiatorAndTenantFromAuthenticatedContext() throws Exception {
        FirstRenderPlanningAndBindingResult planning = result("rj_abc", "req-xyz", "ep-123");
        when(planningAndBindingService.planAndBind(any())).thenReturn(planning);
        when(saveAndStartService.start(any()))
                .thenReturn(new FirstRenderStartResult("render:" + TENANT + ":rj_abc", true));

        mvc.perform(post("/api/first-render/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("profile-42")))
                .andExpect(status().isCreated());

        ArgumentCaptor<FirstRenderPlanningRequest> captor =
                ArgumentCaptor.forClass(FirstRenderPlanningRequest.class);
        verify(planningAndBindingService).planAndBind(captor.capture());
        FirstRenderPlanningRequest captured = captor.getValue();
        assertThat(captured.revisionId()).isEqualTo(REVISION);
        assertThat(captured.profile()).isEqualTo("profile-42");
        assertThat(captured.extent().start()).isEqualTo(MediaTime.ofMillis(0));
        assertThat(captured.extent().end()).isEqualTo(MediaTime.ofMillis(2000));
        assertThat(captured.tenantId()).isEqualTo(TENANT);
        assertThat(captured.initiator().tenantId()).isEqualTo(TENANT);
        assertThat(captured.initiator().actorId()).isEqualTo("actor-a");
    }

    @Test
    void workflowStartedReflectsTheActualStartResult() throws Exception {
        FirstRenderPlanningAndBindingResult planning = result("rj_abc", "req-xyz", "ep-123");
        when(planningAndBindingService.planAndBind(any())).thenReturn(planning);
        when(saveAndStartService.start(any()))
                .thenReturn(new FirstRenderStartResult("render:" + TENANT + ":rj_abc", false));

        mvc.perform(post("/api/first-render/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson("prof-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowStarted").value(false));
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

    @Test
    void exposesOnlyThePhase5Phase6Collaborators() {
        Set<Class<?>> collaborators = Arrays.stream(
                        FirstRenderController.class.getConstructors()[0].getParameterTypes())
                .collect(Collectors.toSet());
        assertThat(collaborators).containsExactlyInAnyOrder(
                FirstRenderPlanningAndBindingService.class,
                FirstRenderSaveAndStartService.class,
                CanonicalActorResolver.class);
    }

    private static MockMvc mockMvc(FirstRenderController controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    private static FirstRenderPlanningAndBindingResult result(
            String jobId, String requestId, String executionPlanId) {
        BoundGraphInputs inputs = mock(BoundGraphInputs.class);
        PhysicalExecutionPlan plan = mock(PhysicalExecutionPlan.class);
        when(plan.planId()).thenReturn(new ExecutionPlanId(executionPlanId));
        when(inputs.physicalPlan()).thenReturn(plan);
        return new FirstRenderPlanningAndBindingResult(
                TENANT, jobId, new RenderRequestId(requestId), inputs);
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
