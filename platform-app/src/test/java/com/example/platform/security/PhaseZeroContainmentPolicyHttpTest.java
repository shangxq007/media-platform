package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class PhaseZeroContainmentPolicyHttpTest {

    // PlatformApplication's explicit component scan does not inherit Boot's test-component filter.
    private static final String TEST_FIXTURE_PROFILE = "phase-zero-containment-policy-http-test";

    private static final List<RouteCase> MUTATION_CASES = List.of(
            mutation(HttpMethod.POST, "/api/extensions/*/execute", "/api/extensions/demo/execute"),
            mutation(HttpMethod.DELETE, "/api/extensions/*", "/api/extensions/demo"),
            mutation(HttpMethod.POST, "/api/extensions/*/rollback", "/api/extensions/demo/rollback"),
            mutation(HttpMethod.POST, "/api/extensions/*/rollback-point", "/api/extensions/demo/rollback-point"),
            mutation(HttpMethod.POST, "/api/extensions/tool-run", "/api/extensions/tool-run"),
            mutation(HttpMethod.POST, "/api/extensions/cli-tools/*/run", "/api/extensions/cli-tools/demo/run"),
            mutation(HttpMethod.POST, "/api/extensions/*/routing-rules", "/api/extensions/demo/routing-rules"),
            mutation(HttpMethod.POST, "/api/render/auto-captions", "/api/render/auto-captions"),
            mutation(HttpMethod.POST, "/api/social/platforms/*/connect", "/api/social/platforms/TWITTER/connect"),
            mutation(HttpMethod.POST, "/api/social/posts/*/publish", "/api/social/posts/post-1/publish"),
            mutation(HttpMethod.POST, "/api/social/posts/*/schedule", "/api/social/posts/post-1/schedule"),
            mutation(HttpMethod.POST, "/api/social/posts/*/retry", "/api/social/posts/post-1/retry"),
            mutation(HttpMethod.POST, "/api/notifications/events", "/api/notifications/events"),
            mutation(HttpMethod.POST, "/api/analytics/internal/rebuild-profiles", "/api/analytics/internal/rebuild-profiles"),
            mutation(HttpMethod.POST, "/api/analytics/internal/rebuild-segments", "/api/analytics/internal/rebuild-segments"),
            mutation(HttpMethod.POST, "/api/billing/cycles/process-due", "/api/billing/cycles/process-due"),
            mutation(HttpMethod.POST, "/api/asset-governance/integrity/scan-global", "/api/asset-governance/integrity/scan-global"),
            mutation(HttpMethod.POST, "/api/asset-governance/segment-cache/cleanup", "/api/asset-governance/segment-cache/cleanup"),
            mutation(HttpMethod.POST, "/api/asset-governance/storage-orphans/purge", "/api/asset-governance/storage-orphans/purge"),
            mutation(HttpMethod.POST, "/api/media/assets/integrity/scan-global", "/api/media/assets/integrity/scan-global"),
            mutation(HttpMethod.POST, "/api/render/jobs/*/cancel", "/api/render/jobs/job-1/cancel"),
            mutation(HttpMethod.POST, "/api/preview/media", "/api/preview/media"),
            mutation(HttpMethod.POST, "/api/remote-worker/register", "/api/remote-worker/register"),
            mutation(HttpMethod.POST, "/api/remote-worker/deregister/*", "/api/remote-worker/deregister/worker-1"),
            mutation(HttpMethod.POST, "/api/remote-worker/heartbeat/*", "/api/remote-worker/heartbeat/worker-1"),
            mutation(HttpMethod.POST, "/api/remote-worker/jobs/*/callback", "/api/remote-worker/jobs/job-1/callback"),
            mutation(HttpMethod.POST, "/api/products/*/dependencies", "/api/products/product-1/dependencies"),
            mutation(HttpMethod.DELETE, "/api/products/*/dependencies/*", "/api/products/product-1/dependencies/dependency-1"),
            mutation(HttpMethod.POST, "/api/tenants/*/notifications/*/retry", "/api/tenants/tenant-1/notifications/notification-1/retry"),
            mutation(HttpMethod.POST, "/api/me/notification-channels/*/verify", "/api/me/notification-channels/binding-1/verify"),
            mutation(HttpMethod.POST, "/api/me/notification-channels/*/test", "/api/me/notification-channels/binding-1/test"),
            mutation(HttpMethod.POST, "/api/admin/notifications/deliveries/*/retry", "/api/admin/notifications/deliveries/delivery-1/retry"),
            mutation(HttpMethod.POST, "/api/artifacts/*/tombstone", "/api/artifacts/artifact-1/tombstone"),
            mutation(HttpMethod.POST, "/api/artifacts/gc/run", "/api/artifacts/gc/run"),
            mutation(HttpMethod.POST, "/api/media/assets/*/tombstone", "/api/media/assets/asset-1/tombstone"),
            mutation(HttpMethod.POST, "/api/media/assets/gc/run", "/api/media/assets/gc/run"));

    private static final List<ReadCase> READ_CASES = List.of(
            read("/api/render/jobs/*/artifacts", "/api/render/jobs/job-1/artifacts"),
            read("/api/render/jobs/*/status-history", "/api/render/jobs/job-1/status-history"),
            read("/api/render/jobs/*/artifacts/*/content", "/api/render/jobs/job-1/artifacts/artifact-1/content"),
            read("/api/render/jobs/*/artifacts/*/access", "/api/render/jobs/job-1/artifacts/artifact-1/access"),
            read("/api/tenants/*/notifications", "/api/tenants/tenant-1/notifications"),
            read("/api/tenants/*/notifications/*", "/api/tenants/tenant-1/notifications/notification-1"),
            read("/api/tenants/*/notifications/*/deliveries", "/api/tenants/tenant-1/notifications/notification-1/deliveries"),
            read("/api/notifications/deliveries", "/api/notifications/deliveries"),
            read("/api/notifications/mock-sent", "/api/notifications/mock-sent"),
            read("/api/admin/notifications/deliveries", "/api/admin/notifications/deliveries"),
            read("/api/admin/notifications/provider-status", "/api/admin/notifications/provider-status"),
            read("/api/social/analytics/posts/*", "/api/social/analytics/posts/post-1"));

    @Test
    void fixturesRemainExcludedFromProductionComponentScanning() {
        assertAll(
                () -> assertTrue(TestBeans.class.isAnnotationPresent(TestConfiguration.class)),
                () -> assertTrue(EnabledSecurity.class.isAnnotationPresent(TestConfiguration.class)),
                () -> assertTrue(SecurityDisabled.class.isAnnotationPresent(TestConfiguration.class)),
                () -> assertTrue(ContainedController.class.isAnnotationPresent(TestComponent.class)),
                () -> assertFixtureProfile(TestBeans.class),
                () -> assertFixtureProfile(EnabledSecurity.class),
                () -> assertFixtureProfile(SecurityDisabled.class),
                () -> assertFixtureProfile(ContainedController.class));
    }

    @Test
    void enabledSecurityRejectsAnonymousAndOrdinaryAuthenticatedBeforeDispatch() throws Exception {
        try (TestHttpContext test = context(EnabledSecurity.class)) {
            test.mvc().perform(post("/api/analytics/internal/rebuild-profiles"))
                    .andExpect(result -> assertEquals(4,
                            result.getResponse().getStatus() / 100));

            test.mvc().perform(post("/api/analytics/internal/rebuild-profiles")
                            .sessionAttr(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                    ordinaryUserContext()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(test.downstream());
        }
    }

    @Test
    void executableMatrixExactlyMatchesPolicyIncludingEveryHeadRead() {
        List<String> expected = allRouteCases().stream().map(RouteCase::matcherKey).toList();
        List<String> actual = PhaseZeroContainmentPolicy.containedRoutes().stream()
                .map(route -> route.method() + " " + route.pattern())
                .toList();

        assertEquals(expected, actual);
    }

    @Test
    void enabledSecurityRejectsEntireMatcherMatrixBeforeDispatch() throws Exception {
        assertEntireMatrixDenied(EnabledSecurity.class, true);
    }

    @Test
    void securityDisabledStillRejectsEntireMatcherMatrixBeforeDispatch() throws Exception {
        assertEntireMatrixDenied(SecurityDisabled.class, false);
    }

    @Test
    void fakeAuthorityAndLegacyIngressRoutesCannotPersistOrExecuteProviders() throws Exception {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            for (String path : List.of(
                    "/api/render/auto-captions",
                    "/api/social/platforms/TWITTER/connect",
                    "/api/social/posts/post-1/publish",
                    "/api/social/posts/post-1/schedule",
                    "/api/social/posts/post-1/retry",
                    "/api/notifications/events")) {
                test.mvc().perform(post(path)).andExpect(status().isForbidden());
            }
            test.mvc().perform(get("/api/social/analytics/posts/post-1"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(test.downstream());
        }
    }

    @Test
    void safeReadStillDispatchesInSecurityDisabledMode() throws Exception {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            test.mvc().perform(get("/api/extensions/demo")).andExpect(status().isOk());
            verify(test.downstream()).invoke();
        }
    }

    private static TestHttpContext context(Class<?> securityConfiguration) {
        Downstream downstream = mock(Downstream.class);
        TestBeans.downstream = downstream;
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(TEST_FIXTURE_PROFILE);
        context.register(securityConfiguration, TestBeans.class, ContainedController.class);
        context.refresh();
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        return new TestHttpContext(context, mvc, downstream);
    }

    private static void assertEntireMatrixDenied(Class<?> configuration, boolean authenticated)
            throws Exception {
        try (TestHttpContext test = context(configuration)) {
            for (RouteCase route : allRouteCases()) {
                var request = MockMvcRequestBuilders.request(route.method(), route.path());
                if (authenticated) {
                    request.sessionAttr(
                            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                            ordinaryUserContext());
                }
                test.mvc().perform(request).andExpect(status().isForbidden());
            }
            verifyNoInteractions(test.downstream());
        }
    }

    private static List<RouteCase> allRouteCases() {
        List<RouteCase> cases = new ArrayList<>(MUTATION_CASES);
        READ_CASES.forEach(read -> {
            cases.add(new RouteCase(HttpMethod.GET, read.pattern(), read.path()));
            cases.add(new RouteCase(HttpMethod.HEAD, read.pattern(), read.path()));
        });
        return List.copyOf(cases);
    }

    private static RouteCase mutation(HttpMethod method, String pattern, String path) {
        return new RouteCase(method, pattern, path);
    }

    private static ReadCase read(String pattern, String path) {
        return new ReadCase(pattern, path);
    }

    private static SecurityContextImpl ordinaryUserContext() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "ordinary-user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new SecurityContextImpl(authentication);
    }

    private static void assertFixtureProfile(Class<?> fixtureType) {
        assertArrayEquals(new String[] {TEST_FIXTURE_PROFILE},
                fixtureType.getAnnotation(Profile.class).value());
    }

    interface Downstream {
        void invoke();
        void persistFakeSuccess();
        void executeProvider();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile(TEST_FIXTURE_PROFILE)
    static class TestBeans {
        static Downstream downstream;

        @Bean
        Downstream downstream() {
            return downstream;
        }
    }

    @TestComponent
    @RestController
    @Profile(TEST_FIXTURE_PROFILE)
    static class ContainedController {
        private final Downstream downstream;

        ContainedController(Downstream downstream) {
            this.downstream = downstream;
        }

        @PostMapping({
            "/api/extensions/{key}/execute",
            "/api/analytics/internal/rebuild-profiles",
            "/api/billing/cycles/process-due",
            "/api/asset-governance/integrity/scan-global",
            "/api/preview/media",
            "/api/remote-worker/register",
            "/api/products/{productId}/dependencies",
            "/api/me/notification-channels/{bindingId}/verify"
        })
        void contained() {
            downstream.invoke();
        }

        @PostMapping({
            "/api/render/auto-captions",
            "/api/social/platforms/{platform}/connect",
            "/api/social/posts/{postId}/publish",
            "/api/social/posts/{postId}/schedule",
            "/api/social/posts/{postId}/retry"
        })
        void fakeProviderAuthority() {
            downstream.executeProvider();
            downstream.persistFakeSuccess();
        }

        @PostMapping("/api/notifications/events")
        void legacyNotificationIngress() {
            downstream.persistFakeSuccess();
            downstream.executeProvider();
        }

        @GetMapping("/api/social/analytics/posts/{postId}")
        void fakeProviderAnalytics() {
            downstream.executeProvider();
            downstream.persistFakeSuccess();
        }

        @GetMapping("/api/extensions/{key}")
        void safeExtensionRead() {
            downstream.invoke();
        }

        @GetMapping("/api/render/jobs/{jobId}/artifacts")
        void unscopedRenderArtifacts() {
            downstream.invoke();
        }

        @GetMapping("/api/notifications/deliveries")
        void globalNotificationDeliveries() {
            downstream.invoke();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(TEST_FIXTURE_PROFILE)
    static class EnabledSecurity {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(SecurityHttpRules::applyApiAuthorization);
            return http.build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(TEST_FIXTURE_PROFILE)
    static class SecurityDisabled {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> {
                        PhaseZeroContainmentPolicy.apply(auth);
                        auth.anyRequest().permitAll();
                    });
            return http.build();
        }
    }

    private record TestHttpContext(
            AnnotationConfigWebApplicationContext context,
            MockMvc mvc,
            Downstream downstream) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }

    private record RouteCase(HttpMethod method, String pattern, String path) {
        String matcherKey() {
            return method + " " + pattern;
        }
    }

    private record ReadCase(String pattern, String path) {}
}
