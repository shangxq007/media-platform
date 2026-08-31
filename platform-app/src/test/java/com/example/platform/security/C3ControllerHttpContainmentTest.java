package com.example.platform.security;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.platform.delivery.api.DeliveryController;
import com.example.platform.render.api.ClientExportController;
import com.example.platform.render.api.RenderController;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.web.assets.AssetEnrichmentController;
import com.example.platform.web.assets.AssetPublishController;
import com.example.platform.web.assets.AssetWorkbenchController;
import com.example.platform.web.assets.MarketplaceController;
import com.example.platform.web.assets.ProductController;
import com.example.platform.web.media.AssetIntegrityScanController;
import com.example.platform.web.render.TimelineGitV1Controller;
import com.example.platform.web.render.TimelineReviewController;
import com.example.platform.web.render.TimelineRevisionController;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class C3ControllerHttpContainmentTest {

    @TestFactory
    Stream<DynamicTest> realControllerMappingsReturnTypedForbiddenWithoutDownstreamCalls() {
        return cases().stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.controllerType().getSimpleName() + " " + testCase.request(),
                () -> {
                    ControllerFixture fixture = instantiate(testCase.controllerType());
                    MockMvc mvc = MockMvcBuilders.standaloneSetup(fixture.controller())
                            .setControllerAdvice(new DenialAdvice())
                            .build();

                    mvc.perform(testCase.request())
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_UNAVAILABLE"));
                    verifyNoInteractions(fixture.dependencies());
                }));
    }

    private static List<HttpCase> cases() {
        return List.of(
                new HttpCase(DeliveryController.class,
                        MockMvcRequestBuilders.get("/api/tenants/tenant-1/delivery/destinations")),
                new HttpCase(TimelineGitV1Controller.class,
                        MockMvcRequestBuilders.get("/api/timeline-git/products/product-1/revisions/current")),
                new HttpCase(TimelineRevisionController.class,
                        MockMvcRequestBuilders.patch(
                                        "/api/render/projects/project-1/timeline/revisions/revision-1/annotation")
                                .contentType(MediaType.APPLICATION_JSON).content("{}")),
                new HttpCase(TimelineReviewController.class,
                        MockMvcRequestBuilders.get("/api/render/projects/project-1/timeline/reviews")),
                new HttpCase(ProductController.class,
                        MockMvcRequestBuilders.get("/api/products/product-1")),
                new HttpCase(AssetWorkbenchController.class,
                        MockMvcRequestBuilders.get("/api/assets/asset-1/workspace")),
                new HttpCase(RenderController.class,
                        MockMvcRequestBuilders.get("/api/render/jobs/job-1/artifacts")),
                new HttpCase(ClientExportController.class,
                        MockMvcRequestBuilders.get("/api/render/client-exports")),
                new HttpCase(AssetEnrichmentController.class,
                        MockMvcRequestBuilders.get(
                                "/api/projects/project-1/assets/asset-1/enrichment-status")),
                new HttpCase(AssetIntegrityScanController.class,
                        MockMvcRequestBuilders.post("/api/media/assets/integrity/scan")
                                .param("projectId", "project-1")),
                new HttpCase(AssetPublishController.class,
                        MockMvcRequestBuilders.post(
                                "/api/projects/project-1/assets/asset-1/reject-review")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.get("/api/marketplace/search")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.get("/api/marketplace/listings")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.get("/api/marketplace/listings/listing-1")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.get("/api/marketplace/assets/asset-1/listing")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.patch("/api/marketplace/listings/listing-1/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}")),
                new HttpCase(MarketplaceController.class,
                        MockMvcRequestBuilders.get("/api/marketplace/discovery")));
    }

    private static ControllerFixture instantiate(Class<?> controllerType) throws Exception {
        Constructor<?> constructor = controllerType.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(type -> org.mockito.Mockito.mock(type))
                .toArray();
        return new ControllerFixture(constructor.newInstance(dependencies), dependencies);
    }

    private record HttpCase(Class<?> controllerType, MockHttpServletRequestBuilder request) {}

    private record ControllerFixture(Object controller, Object[] dependencies) {}

    @RestControllerAdvice
    static class DenialAdvice {
        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<Map<String, String>> deny(AuthorizationDeniedException failure) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("reasonCode", failure.decision().reasonCode()));
        }
    }
}
