package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Executes the containment manifest itself through both supported security modes. */
class PhaseZeroContainmentPolicyHttpTest {

    private static final String PROFILE = "phase-zero-containment-policy-http-test";
    private static final List<HttpMethod> EXTERNAL_METHODS = List.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS);

    @Test
    void manifestIsDeniedBeforeDispatchInEnabledAndSecurityDisabledModes() throws Exception {
        assertManifestDenied(EnabledSecurity.class, true);
        assertManifestDenied(SecurityDisabled.class, false);
    }

    @Test
    void familyRulesCoverEveryHttpMethodIncludingHeadAndOptions() {
        for (String family : PhaseZeroContainmentPolicy.containedFamilies()) {
            String path = sample(family);
            for (HttpMethod method : EXTERNAL_METHODS) {
                assertTrue(PhaseZeroContainmentPolicy.contains(method, path),
                        () -> method + " " + path + " escaped " + family);
            }
        }
    }

    @Test
    void fixtureIsImpossibleToActivateInProductionScanning() {
        assertArrayEquals(new String[] {PROFILE}, CatchAllController.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[] {PROFILE}, Beans.class.getAnnotation(Profile.class).value());
    }

    @Test
    void unrelatedReadStillDispatches() throws Exception {
        try (TestHttpContext test = context(SecurityDisabled.class)) {
            test.mvc().perform(MockMvcRequestBuilders.get("/api/extensions/demo"))
                    .andExpect(status().isOk());
            verify(test.downstream()).invoke();
        }
    }

    private static void assertManifestDenied(Class<?> securityConfiguration, boolean authenticated)
            throws Exception {
        try (TestHttpContext test = context(securityConfiguration)) {
            for (RequestCase requestCase : manifestCases()) {
                var request = MockMvcRequestBuilders.request(requestCase.method(), requestCase.path());
                if (authenticated) {
                    request.sessionAttr(
                            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                            authenticatedContext());
                }
                test.mvc().perform(request).andExpect(status().isForbidden());
            }
            verifyNoInteractions(test.downstream());
        }
    }

    private static List<RequestCase> manifestCases() {
        List<RequestCase> cases = new ArrayList<>();
        for (String family : PhaseZeroContainmentPolicy.containedFamilies()) {
            for (HttpMethod method : EXTERNAL_METHODS) {
                cases.add(new RequestCase(method, sample(family)));
            }
        }
        PhaseZeroContainmentPolicy.containedRoutes().forEach(route ->
                cases.add(new RequestCase(route.method(), sample(route.pattern()))));
        return List.copyOf(cases);
    }

    private static String sample(String pattern) {
        return pattern.replace("**", "probe").replace("*", "probe");
    }

    private static SecurityContextImpl authenticatedContext() {
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                "ordinary-user", "n/a", List.of()));
    }

    private static TestHttpContext context(Class<?> securityConfiguration) {
        Beans.downstream = mock(Downstream.class);
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(PROFILE);
        context.register(securityConfiguration, Beans.class, CatchAllController.class);
        context.refresh();
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        return new TestHttpContext(context, mvc, Beans.downstream);
    }

    interface Downstream {
        void invoke();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile(PROFILE)
    static class Beans {
        static Downstream downstream;

        @Bean Downstream downstream() {
            return downstream;
        }
    }

    @TestComponent
    @RestController
    @Profile(PROFILE)
    static class CatchAllController {
        private final Downstream downstream;

        CatchAllController(Downstream downstream) {
            this.downstream = downstream;
        }

        @RequestMapping("/**")
        void dispatch() {
            downstream.invoke();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(PROFILE)
    static class EnabledSecurity {
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(auth -> {
                PhaseZeroContainmentPolicy.apply(auth);
                auth.anyRequest().authenticated();
            });
            return http.build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Profile(PROFILE)
    static class SecurityDisabled {
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(auth -> {
                PhaseZeroContainmentPolicy.apply(auth);
                auth.anyRequest().permitAll();
            });
            return http.build();
        }
    }

    private record RequestCase(HttpMethod method, String path) {}

    private record TestHttpContext(
            AnnotationConfigWebApplicationContext context,
            MockMvc mvc,
            Downstream downstream) implements AutoCloseable {
        @Override public void close() {
            context.close();
        }
    }
}
