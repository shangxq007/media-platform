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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class PhaseZeroContainmentPolicyHttpTest {

    // PlatformApplication's explicit component scan does not inherit Boot's test-component filter.
    private static final String TEST_FIXTURE_PROFILE = "phase-zero-containment-policy-http-test";

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
    void securityDisabledStillRejectsEveryRepresentativeFamilyBeforeDispatch() throws Exception {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            List<String> paths = List.of(
                    "/api/extensions/demo/execute",
                    "/api/analytics/internal/rebuild-profiles",
                    "/api/billing/cycles/process-due",
                    "/api/asset-governance/integrity/scan-global",
                    "/api/preview/media",
                    "/api/remote-worker/register",
                    "/api/products/product-1/dependencies",
                    "/api/me/notification-channels/binding-1/verify");

            for (String path : paths) {
                test.mvc().perform(post(path)).andExpect(status().isForbidden());
            }
            test.mvc().perform(get("/api/render/jobs/job-1/artifacts"))
                    .andExpect(status().isForbidden());
            test.mvc().perform(get("/api/notifications/deliveries"))
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
}
