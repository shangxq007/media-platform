package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.security.RuntimeRoutePolicyVerifier.RoutePolicyVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class RuntimeRoutePolicyNegativeControlTest {

    private static final String PROFILE = "runtime-route-policy-negative-control";

    @Test
    void emptyRuntimeRouteUniverseFailsClosed() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebMvcConfiguration.class);
            context.refresh();

            var mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            var discovery = new RuntimeMvcRouteDiscovery(mapping);
            assertTrue(discovery.discoverApplicationRoutes().isEmpty());

            var verifier = new RuntimeRoutePolicyVerifier(discovery);
            RoutePolicyVerificationException failure = assertThrows(
                    RoutePolicyVerificationException.class, verifier::verify);
            assertTrue(failure.getMessage().contains("empty route universe"));
        }
    }

    @Test
    void actualInjectedDangerousMappingFailsWhenPolicyOmitsIt() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().setActiveProfiles(PROFILE);
            context.register(WebMvcConfiguration.class, OmittedExternalControlController.class);
            context.refresh();

            var mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            var discovery = new RuntimeMvcRouteDiscovery(mapping);
            var actual = discovery.discoverApplicationRoutes();

            assertEquals(1, actual.size());
            assertEquals(HttpMethod.POST, actual.getFirst().method());
            assertEquals("/api/runtime-policy-negative-control/execute", actual.getFirst().path());
            assertEquals(OmittedExternalControlController.class.getName(), actual.getFirst().controller());
            assertTrue(actual.getFirst().handlerMethod().contains("execute"));

            var verifier = new RuntimeRoutePolicyVerifier(discovery);
            RoutePolicyVerificationException failure = assertThrows(
                    RoutePolicyVerificationException.class, verifier::verify);
            assertTrue(failure.getMessage().contains("POST /api/runtime-policy-negative-control/execute"));
            assertTrue(failure.getMessage().contains(OmittedExternalControlController.class.getName()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebMvcConfiguration {}

    @TestComponent
    @RestController
    @Profile(PROFILE)
    static class OmittedExternalControlController {
        @PostMapping("/api/runtime-policy-negative-control/execute")
        void execute() {}
    }
}
