package com.example.platform.web.media;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.invocation.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.web.render.TimelineProjectAuthorizationService;
import com.example.platform.config.firstrender.FirstRenderSubmissionService;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpMediaToolsControllerTest {
    final ObjectMapper json=new ObjectMapper();
    final CanonicalActorResolver actors=mock(CanonicalActorResolver.class);
    final FirstRenderSubmissionService submissions=mock(FirstRenderSubmissionService.class);
    final TimelineProjectAuthorizationService access=mock(TimelineProjectAuthorizationService.class);
    final OperationInvocationPort operations=mock(OperationInvocationPort.class);
    final jakarta.validation.ValidatorFactory validators=jakarta.validation.Validation.buildDefaultValidatorFactory();
    final McpMediaToolsController controller=new McpMediaToolsController(actors,submissions,
        mock(com.example.platform.timeline.api.revision.TimelineRevisionQueries.class),access,operations,
        mock(com.example.platform.render.app.RenderJobService.class),mock(com.example.platform.render.api.GraphRenderLifecycle.class),
        mock(com.example.platform.delivery.app.DeliveryService.class),mock(com.example.platform.artifact.app.ArtifactApplicationService.class),
        mock(com.example.platform.artifact.app.ArtifactProjectAuthorizationPort.class),json,validators.getValidator());
    @BeforeEach void context(){TenantContext.set("tenant");when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("editor","tenant",Set.of(),"unit")));}
    @AfterEach void clear(){TenantContext.clear();validators.close();}
    JsonNode rpc(String method,Map<String,Object> params){var result=controller.rpc(new McpMediaToolsController.Rpc("2.0",1,method,params));assertEquals("private, no-store",result.getHeaders().getCacheControl());return json.valueToTree(result.getBody());}
    @Test void actualJsonRpcInitializeAndNineProductToolsHaveClosedSchemas() {
        assertEquals("2025-03-26",rpc("initialize",Map.of()).at("/result/protocolVersion").asText());
        var tools=rpc("tools/list",Map.of()).at("/result/tools");assertEquals(9,tools.size());Set<String> names=new HashSet<>();
        tools.forEach(t->{assertTrue(names.add(t.get("name").asText()));assertFalse(t.at("/inputSchema/additionalProperties").asBoolean());});
        assertTrue(names.containsAll(Set.of("timeline.revision","timeline.apply_operation","render.submit","render.submit_incremental","render.job","delivery.create","delivery.result","artifact.query")));
        assertFalse(names.contains("render_timeline"));verifyNoInteractions(submissions,operations,access);
    }
    @Test void retiredPipelineToolIsNotACompatibilityIngress() {
        assertEquals(-32602,rpc("tools/call",Map.of("name","render_timeline","arguments",Map.of())).at("/error/code").asInt());
        verifyNoInteractions(submissions,operations,access);
    }
    @Test void authenticatedTenantMismatchCannotEvenDiscoverTools() {
        TenantContext.set("foreign");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->rpc("tools/list",Map.of()));verifyNoInteractions(submissions,operations,access);
    }
    @Test void noAuthenticatedActorCannotUseTheStatelessEndpoint() {
        when(actors.resolveCurrentActor()).thenReturn(Optional.empty());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->rpc("initialize",Map.of()));
    }
    @Test void unknownProviderAndFreeTenantFieldsAreRejectedBeforeSubmission() {
        var request=new LinkedHashMap<String,Object>();request.put("revisionId","rev");request.put("profile","default");request.put("outputFormat","MP4");request.put("extent",Map.of("startMillis",0,"endMillis",1000,"frameRateNumerator",4,"frameRateDenominator",1));request.put("providerId","unbound");
        assertEquals(-32602,rpc("tools/call",Map.of("name","render.submit","arguments",Map.of("request",request,"idempotencyKey","intent"))).at("/error/code").asInt());
        assertEquals(-32602,rpc("tools/call",Map.of("name","render.job","arguments",Map.of("projectId","project","renderJobId","job","tenantId","foreign"))).at("/error/code").asInt());verifyNoInteractions(submissions);
    }
    @Test void operationCannotUseAProviderOrRawDagInsteadOfACanonicalCommand() {
        assertEquals(-32602,rpc("tools/call",Map.of("name","timeline.apply_operation","arguments",Map.of("projectId","project","command",Map.of("dag",Map.of())))).at("/error/code").asInt());verifyNoInteractions(operations);
    }
    @Test void invalidJsonRpcIdentityIsRejected() {
        var response=controller.rpc(new McpMediaToolsController.Rpc("2.0",Map.of("invalid",true),"tools/list",null));assertEquals(-32600,json.valueToTree(response.getBody()).at("/error/code").asInt());
    }
    @Test void actualHttpMessageConversionUsesPlainJsonRpcValues() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/mcp/media")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":\"wire-1\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value("wire-1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.serverInfo.name").value("media-platform"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/mcp/media")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.tools.length()").value(9))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.tools[0].inputSchema.type").value("object"));
    }
}
