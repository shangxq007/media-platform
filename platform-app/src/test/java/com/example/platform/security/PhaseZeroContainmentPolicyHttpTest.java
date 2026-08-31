package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Exercises concrete RequestMappings through the same policy used by production chains. */
class PhaseZeroContainmentPolicyHttpTest {

    private static final String PROFILE = "phase-zero-containment-policy-http-test";
    private static final String CORS_ORIGIN = "https://client.example";

    @Test
    void securityDisabledDispatchesOnlyExplicitPublicSafeReads() throws Exception {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            test.mvc().perform(MockMvcRequestBuilders.get("/healthz")).andExpect(status().isOk());
            verify(test.downstream()).invoke("public");

            test.mvc().perform(MockMvcRequestBuilders.get("/api/me/dashboard"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/effect-packs"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.get("/api/admin/feature-flags"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/mcp/probe"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/internal/outbox/probe"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/render/probe"))
                    .andExpect(status().isForbidden());
            for (String path : projectDashboardAliases()) {
                test.mvc().perform(MockMvcRequestBuilders.get(path))
                        .andExpect(status().isForbidden());
            }
            for (C3Route route : c3ContainedRoutes()) {
                test.mvc().perform(MockMvcRequestBuilders.request(route.method(), route.alias()))
                        .andExpect(status().isForbidden());
            }
            test.mvc().perform(MockMvcRequestBuilders.post("/api/dev/auth/token"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.options(
                            "/api/runtime-policy-negative-control/execute-options"))
                    .andExpect(status().isForbidden());
            verifyNoMoreInteractions(test.downstream());
        }
    }

    @Test
    void enabledSecurityAppliesTheSameConcreteRouteClassifications() throws Exception {
        try (TestHttpContext test = context(EnabledSecurity.class)) {
            var ordinary = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
            test.mvc().perform(MockMvcRequestBuilders.get("/healthz"))
                    .andExpect(status().isOk());
            test.mvc().perform(MockMvcRequestBuilders.get("/api/me/dashboard")
                            .sessionAttr(ordinary, authenticated(false)))
                    .andExpect(status().isOk());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/effect-packs")
                            .sessionAttr(ordinary, authenticated(false)))
                    .andExpect(status().isOk());
            test.mvc().perform(MockMvcRequestBuilders.get("/api/admin/feature-flags")
                            .sessionAttr(ordinary, authenticated(false)))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.get("/api/admin/feature-flags")
                            .sessionAttr(ordinary, authenticated(true)))
                    .andExpect(status().isOk());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/mcp/probe")
                            .sessionAttr(ordinary, authenticated(true)))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/internal/outbox/probe")
                            .sessionAttr(ordinary, authenticated(true)))
                    .andExpect(status().isForbidden());
            test.mvc().perform(MockMvcRequestBuilders.post("/api/render/probe")
                            .sessionAttr(ordinary, authenticated(true)))
                    .andExpect(status().isForbidden());
            for (String path : projectDashboardAliases()) {
                test.mvc().perform(MockMvcRequestBuilders.get(path)
                                .sessionAttr(ordinary, authenticated(true)))
                        .andExpect(status().isForbidden());
            }
            for (C3Route route : c3ContainedRoutes()) {
                test.mvc().perform(MockMvcRequestBuilders.request(route.method(), route.alias())
                                .sessionAttr(ordinary, authenticated(true)))
                        .andExpect(status().isForbidden());
            }
            test.mvc().perform(MockMvcRequestBuilders.post("/api/dev/auth/token"))
                    .andExpect(status().isOk());
            test.mvc().perform(MockMvcRequestBuilders.options(
                            "/api/runtime-policy-negative-control/execute-options"))
                    .andExpect(status().isForbidden());
            verify(test.downstream()).invoke("public");
            verify(test.downstream()).invoke("read");
            verify(test.downstream()).invoke("mutation");
            verify(test.downstream()).invoke("admin");
            verify(test.downstream()).invoke("test");
            verifyNoMoreInteractions(test.downstream());
        }
    }

    @Test
    void genuineCorsPreflightDoesNotDispatchTheActualOptionsHandler() throws Exception {
        for (Class<?> securityConfiguration : List.of(EnabledSecurity.class, SecurityDisabled.class)) {
            try (TestHttpContext test = context(securityConfiguration)) {
                test.mvc().perform(MockMvcRequestBuilders.options(
                                "/api/runtime-policy-negative-control/execute-options")
                                .header("Origin", CORS_ORIGIN)
                                .header("Access-Control-Request-Method", "POST"))
                        .andExpect(status().isOk());
                verifyNoMoreInteractions(test.downstream());
            }
        }
    }

    @Test
    void classificationsAreAppliedToActualRegisteredMappings() {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            var mapping = test.context().getBean(
                    "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            var discovered = new RuntimeMvcRouteDiscovery(mapping).discoverApplicationRoutes();
            Map<String, Optional<PhaseZeroContainmentPolicy.Classification>> actual = discovered.stream()
                    .collect(Collectors.toMap(
                            route -> route.method() + " " + route.path(),
                            route -> PhaseZeroContainmentPolicy.classify(route.method(), route.path())));

            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.PUBLIC_SAFE),
                    actual.get("GET /healthz"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.AUTHENTICATED_READ),
                    actual.get("GET /api/me/dashboard"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.AUTHENTICATED_MUTATION),
                    actual.get("POST /api/effect-packs"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.ADMIN_ONLY),
                    actual.get("GET /api/admin/feature-flags"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.INTERNAL_CONTROL_PLANE),
                    actual.get("POST /api/mcp/probe"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.INTERNAL_CONTROL_PLANE),
                    actual.get("POST /api/internal/outbox/probe"));
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.DISABLED_CONTAINED),
                    actual.get("POST /api/render/probe"));
            for (String path : projectDashboardMappings()) {
                assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.DISABLED_CONTAINED),
                        actual.get("GET " + path));
            }
            for (C3Route route : c3ContainedRoutes()) {
                assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.DISABLED_CONTAINED),
                        actual.get(route.method() + " " + route.mapping()), route.mapping());
            }
            assertEquals(Optional.of(PhaseZeroContainmentPolicy.Classification.TEST_ONLY),
                    actual.get("POST /api/dev/auth/token"));
            assertEquals(Optional.empty(),
                    actual.get("OPTIONS /api/runtime-policy-negative-control/execute-options"));
        }
    }

    private static SecurityContextImpl authenticated(boolean admin) {
        var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.<SimpleGrantedAuthority>of();
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                "user", "n/a", authorities));
    }

    private static List<String> projectDashboardAliases() {
        return List.of(
                "/api/projects/project-1/dashboard",
                "/api/projects/project-1/dashboard/activity",
                "/api/projects/project-1/dashboard/pending",
                "/api/projects/project-1/dashboard/health");
    }

    private static List<String> projectDashboardMappings() {
        return List.of(
                "/api/projects/{projectId}/dashboard",
                "/api/projects/{projectId}/dashboard/activity",
                "/api/projects/{projectId}/dashboard/pending",
                "/api/projects/{projectId}/dashboard/health");
    }

    private static List<C3Route> c3ContainedRoutes() {
        return List.of(
                new C3Route(HttpMethod.POST,
                        "/api/tenants/tenant-1/delivery/destinations",
                        "/api/tenants/{tenantId}/delivery/destinations"),
                new C3Route(HttpMethod.POST,
                        "/api/timeline-git/products/product-1/revisions",
                        "/api/timeline-git/products/{productId}/revisions"),
                new C3Route(HttpMethod.POST,
                        "/api/render/projects/project-1/timeline/revisions/merge",
                        "/api/render/projects/{projectId}/timeline/revisions/merge"),
                new C3Route(HttpMethod.POST,
                        "/api/render/projects/project-1/timeline/reviews",
                        "/api/render/projects/{projectId}/timeline/reviews"),
                new C3Route(HttpMethod.GET,
                        "/api/products/product-1",
                        "/api/products/{productId}"),
                new C3Route(HttpMethod.GET,
                        "/api/assets/asset-1/workspace",
                        "/api/assets/{assetId}/workspace"),
                new C3Route(HttpMethod.POST,
                        "/api/tenants/tenant-1/projects/project-1/render-jobs",
                        "/api/tenants/{tenantId}/projects/{projectId}/render-jobs"),
                new C3Route(HttpMethod.GET,
                        "/api/render/client-exports",
                        "/api/render/client-exports"),
                new C3Route(HttpMethod.POST,
                        "/api/projects/project-1/assets/asset-1/enrich",
                        "/api/projects/{projectId}/assets/{assetId}/enrich"),
                new C3Route(HttpMethod.POST,
                        "/api/media/assets/integrity/scan",
                        "/api/media/assets/integrity/scan"),
                new C3Route(HttpMethod.POST,
                        "/api/projects/project-1/assets/asset-1/publish",
                        "/api/projects/{projectId}/assets/{assetId}/publish"));
    }

    private static TestHttpContext context(Class<?> securityConfiguration) {
        Beans.downstream = mock(Downstream.class);
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(PROFILE);
        context.register(securityConfiguration, Beans.class, ClassifiedController.class);
        context.refresh();
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        return new TestHttpContext(context, mvc, Beans.downstream);
    }

    interface Downstream {
        void invoke(String route);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Profile(PROFILE)
    static class Beans {
        static Downstream downstream;

        @Bean Downstream downstream() {
            return downstream;
        }

        @Bean CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of(CORS_ORIGIN));
            configuration.setAllowedMethods(List.of("POST"));
            configuration.setAllowedHeaders(List.of("*"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }
    }

    @TestComponent
    @RestController
    @Profile(PROFILE)
    static class ClassifiedController {
        private final Downstream downstream;

        ClassifiedController(Downstream downstream) {
            this.downstream = downstream;
        }

        @GetMapping("/healthz") void publicRead() { downstream.invoke("public"); }
        @GetMapping("/api/me/dashboard") void authenticatedRead() { downstream.invoke("read"); }
        @PostMapping("/api/effect-packs") void authenticatedMutation() { downstream.invoke("mutation"); }
        @GetMapping("/api/admin/feature-flags") void admin() { downstream.invoke("admin"); }
        @PostMapping("/api/mcp/probe") void internalControl() { downstream.invoke("internal"); }
        @PostMapping("/api/internal/outbox/probe") void internalOutboxControl() {
            downstream.invoke("internal-outbox");
        }
        @PostMapping("/api/render/probe") void disabled() { downstream.invoke("disabled"); }
        @GetMapping("/api/projects/{projectId}/dashboard")
        void projectDashboard() { downstream.invoke("project-dashboard"); }
        @GetMapping("/api/projects/{projectId}/dashboard/activity")
        void projectDashboardActivity() { downstream.invoke("project-dashboard-activity"); }
        @GetMapping("/api/projects/{projectId}/dashboard/pending")
        void projectDashboardPending() { downstream.invoke("project-dashboard-pending"); }
        @GetMapping("/api/projects/{projectId}/dashboard/health")
        void projectDashboardHealth() { downstream.invoke("project-dashboard-health"); }
        @PostMapping("/api/tenants/{tenantId}/delivery/destinations")
        void deliveryDestination() { downstream.invoke("c3-delivery"); }
        @PostMapping("/api/timeline-git/products/{productId}/revisions")
        void timelineSave() { downstream.invoke("c3-timeline-save"); }
        @PostMapping("/api/render/projects/{projectId}/timeline/revisions/merge")
        void timelineMerge() { downstream.invoke("c3-timeline-merge"); }
        @PostMapping("/api/render/projects/{projectId}/timeline/reviews")
        void timelineReview() { downstream.invoke("c3-timeline-review"); }
        @GetMapping("/api/products/{productId}")
        void productRead() { downstream.invoke("c3-product"); }
        @GetMapping("/api/assets/{assetId}/workspace")
        void assetWorkbenchRead() { downstream.invoke("c3-asset-workbench"); }
        @PostMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs")
        void renderCreate() { downstream.invoke("c3-render"); }
        @GetMapping("/api/render/client-exports")
        void clientExport() { downstream.invoke("c3-client-export"); }
        @PostMapping("/api/projects/{projectId}/assets/{assetId}/enrich")
        void assetEnrichment() { downstream.invoke("c3-enrichment"); }
        @PostMapping("/api/media/assets/integrity/scan")
        void integrityScan() { downstream.invoke("c3-integrity"); }
        @PostMapping("/api/projects/{projectId}/assets/{assetId}/publish")
        void assetPublish() { downstream.invoke("c3-publish"); }
        @PostMapping("/api/dev/auth/token") void testOnly() { downstream.invoke("test"); }
        @RequestMapping(
                path = "/api/runtime-policy-negative-control/execute-options",
                method = RequestMethod.OPTIONS)
        void unclassifiedOptions() { downstream.invoke("unclassified-options"); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(PROFILE)
    static class EnabledSecurity {
        @Bean SecurityFilterChain chain(
                HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(PhaseZeroContainmentPolicy::applyEnabled);
            return http.build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(PROFILE)
    static class SecurityDisabled {
        @Bean SecurityFilterChain chain(
                HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(PhaseZeroContainmentPolicy::applySecurityDisabled);
            return http.build();
        }
    }

    private record TestHttpContext(
            AnnotationConfigWebApplicationContext context,
            MockMvc mvc,
            Downstream downstream) implements AutoCloseable {
        @Override public void close() {
            context.close();
        }
    }

    private record C3Route(HttpMethod method, String alias, String mapping) {}
}
