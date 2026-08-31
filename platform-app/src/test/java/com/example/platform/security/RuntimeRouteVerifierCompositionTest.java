package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.security.RuntimeRoutePolicyVerifier.RoutePolicyVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class RuntimeRouteVerifierCompositionTest {

    private static final String PROFILE = "runtime-route-verifier-composition-test";

    @Test
    void nonWebContextStartsWithoutRuntimeRouteVerifierBeans() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + PROFILE)
                .withUserConfiguration(RuntimeRouteVerifierBeans.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(RuntimeMvcRouteDiscovery.class).isEmpty());
                    assertTrue(context.getBeansOfType(RuntimeRoutePolicyVerifier.class).isEmpty());
                });
    }

    @Test
    void servletContextHasVerifierBeansAndNonEmptyRuntimeMappings() {
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + PROFILE)
                .withUserConfiguration(
                        RuntimeRouteVerifierBeans.class,
                        WebMvcConfiguration.class,
                        ClassifiedController.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(RuntimeMvcRouteDiscovery.class).size());
                    assertEquals(1, context.getBeansOfType(RuntimeRoutePolicyVerifier.class).size());
                    assertEquals(1, context.getBeansOfType(RequestMappingHandlerMapping.class).size());

                    var discovery = context.getBean(RuntimeMvcRouteDiscovery.class);
                    var discovered = discovery.discoverApplicationRoutes();
                    assertFalse(discovered.isEmpty());
                    assertTrue(discovered.stream().anyMatch(route -> route.path().equals("/healthz")));

                    var report = context.getBean(RuntimeRoutePolicyVerifier.class).verify();
                    assertEquals(discovered, report.routes());
                    assertEquals(discovered.size(), report.routeCount());
                });
    }

    @Test
    void servletContextFailsStartupWhenRuntimeMappingIsUnclassified() {
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + PROFILE)
                .withUserConfiguration(
                        RuntimeRouteVerifierBeans.class,
                        WebMvcConfiguration.class,
                        OmittedRouteController.class)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    RoutePolicyVerificationException policyFailure = findCause(
                            failure, RoutePolicyVerificationException.class);
                    assertTrue(policyFailure.getMessage().contains(
                            "POST /api/runtime-route-composition/omitted"));
                    assertTrue(policyFailure.getMessage().contains(
                            OmittedRouteController.class.getName()));
                });
    }

    @Test
    void servletContextFailsStartupForEmptyApplicationRouteUniverse() {
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + PROFILE)
                .withUserConfiguration(RuntimeRouteVerifierBeans.class, WebMvcConfiguration.class)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    RoutePolicyVerificationException policyFailure = findCause(
                            failure, RoutePolicyVerificationException.class);
                    assertTrue(policyFailure.getMessage().contains("empty route universe"));
                });
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> expectedType) {
        Throwable candidate = failure;
        while (candidate != null && !expectedType.isInstance(candidate)) {
            candidate = candidate.getCause();
        }
        assertInstanceOf(expectedType, candidate);
        return expectedType.cast(candidate);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({RuntimeMvcRouteDiscovery.class, RuntimeRoutePolicyVerifier.class})
    @Profile(PROFILE)
    static class RuntimeRouteVerifierBeans {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Profile(PROFILE)
    static class WebMvcConfiguration {}

    @RestController
    @Profile(PROFILE)
    static class ClassifiedController {
        @GetMapping("/healthz")
        void health() {}
    }

    @RestController
    @Profile(PROFILE)
    static class OmittedRouteController {
        @PostMapping("/api/runtime-route-composition/omitted")
        void omitted() {}
    }
}
