package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.security.PhaseZeroContainmentPolicy;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.AntPathMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
class MvcRouteInventoryTest extends PostgresTestContainerSupport {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void captureRouteInventory() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        StringBuilder sb = new StringBuilder();
        sb.append("=== MVC ROUTE INVENTORY ===\n");
        sb.append("Total handler mappings: ").append(handlerMethods.size()).append("\n");
        sb.append("---\n");

        int count = 0;
        for (var entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            String beanType = handler.getBeanType().getSimpleName();
            String methodName = handler.getMethod().getName();
            String fullPathCondition = info.toString();

            sb.append(String.format("[%d] %s | %s.%s%n", count, fullPathCondition, beanType, methodName));
            count++;
        }

        sb.append("=== END ===\n");
        Files.writeString(Path.of("/tmp/mvc-route-inventory.txt"), sb.toString());

        // AR-W2-PTEH / public-api-contract.tsv: the W2 V1 public surface exposes
        // exactly nine frozen routes under /api/tenants/{tenantId}/workflow-definitions.
        // Counted as the nine handler methods on UserWorkflowDefinitionController — the
        // authoritative signal for the frozen route surface (public-api-contract.tsv).
        long w2Routes = handlerMethods.values().stream()
                .filter(h -> h.getBeanType().getSimpleName()
                        .equals("UserWorkflowDefinitionController"))
                .map(h -> h.getMethod().getName())
                .distinct()
                .count();
        assertEquals((long) EXPECTED_W2_ROUTE_COUNT, w2Routes,
                "W2 public route count must be exactly " + EXPECTED_W2_ROUTE_COUNT
                        + " (public-api-contract.tsv); see /tmp/mvc-route-inventory.txt");
    }

    @Test
    void realDangerousMappingsAreMechanicallyCoveredByTheContainmentManifest() {
        Map<RequestMappingInfo, HandlerMethod> mappings = requestMappingHandlerMapping.getHandlerMethods();

        for (var entry : mappings.entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!isDangerousHandler(handler)) {
                continue;
            }
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    entry.getKey().getMethodsCondition().getMethods();
            assertFalse(methods.isEmpty(), () -> "dangerous all-method mapping: " + entry);
            for (String pattern : entry.getKey().getPatternValues()) {
                for (var requestMethod : methods) {
                    HttpMethod method = HttpMethod.valueOf(requestMethod.name());
                    assertTrue(PhaseZeroContainmentPolicy.contains(method, pattern),
                            () -> "uncontained real mapping " + method + " " + pattern
                                    + " -> " + handler);
                    if (method == HttpMethod.GET) {
                        assertTrue(PhaseZeroContainmentPolicy.contains(HttpMethod.HEAD, pattern),
                                () -> "HEAD escaped contained GET " + pattern + " -> " + handler);
                    }
                }
            }
        }

        AntPathMatcher matcher = new AntPathMatcher();
        for (PhaseZeroContainmentPolicy.ContainedRoute route
                : PhaseZeroContainmentPolicy.containedRoutes()) {
            assertTrue(mappings.entrySet().stream().anyMatch(entry ->
                            entry.getKey().getPatternValues().stream().anyMatch(pattern ->
                                    matcher.match(route.pattern(), normalize(pattern)))
                            && mappingSupports(entry.getKey(), route.method())),
                    () -> "stale synthetic containment route " + route.method() + " " + route.pattern());
        }
        Set<String> inactiveGuardedFamilies = new TreeSet<>();
        for (String family : PhaseZeroContainmentPolicy.containedFamilies()) {
            boolean active = mappings.keySet().stream().flatMap(info -> info.getPatternValues().stream())
                    .anyMatch(pattern -> matcher.match(family, normalize(pattern)));
            if (!active) {
                inactiveGuardedFamilies.add(family);
            }
        }
        assertEquals(Set.of("/api/social/**"), inactiveGuardedFamilies,
                "only the source-present but composition-inactive social surface may be guarded preemptively");
    }

    @Test
    void affectedScheduledAndListenerReachabilityHasNoAlternateEntryPoint() {
        Set<String> actual = new TreeSet<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null || !isAffectedAsyncPackage(type.getPackageName())) {
                continue;
            }
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)
                        || AnnotatedElementUtils.hasAnnotation(method, EventListener.class)) {
                    actual.add(type.getName() + "#" + method.getName());
                }
            }
        }
        assertEquals(Set.of(), actual,
                "affected schedulers/listeners must remain inactive until their authority phase");
    }

    private static boolean isDangerousHandler(HandlerMethod handler) {
        String type = handler.getBeanType().getSimpleName();
        String method = handler.getMethod().getName();
        return Set.of(
                        "McpMediaToolsController",
                        "ProductizationApi",
                        "SocialPublishController",
                        "RemoteWorkerController",
                        "AiController",
                        "NaturalLanguageQueryController",
                        "FederationQueryController",
                        "PolicyController",
                        "CaptionTemplateRenderController")
                .contains(type)
                || Set.of(
                        "MeController#getNotifications",
                        "MeController#markNotificationRead",
                        "MeController#getExports",
                        "MeController#getReports",
                        "MeController#getFeedback",
                        "MeController#submitFeedback",
                        "MeBillingController#invoices",
                        "MeBillingController#invoice",
                        "RenderController#getArtifactAccessScoped",
                        "RenderController#getArtifactAccess",
                        "RenderController#submitIncrementalRenderJob",
                        "RenderController#startRenderJob",
                        "RenderController#aiEditTimeline",
                        "AnalyticsController#rebuildProfiles",
                        "AnalyticsController#rebuildSegments",
                        "TimelineRevisionController#render")
                .contains(type + "#" + method);
    }

    private static boolean isAffectedAsyncPackage(String packageName) {
        return packageName.startsWith("com.example.platform.social")
                || packageName.startsWith("com.example.platform.render.app.aaf")
                || packageName.startsWith("com.example.platform.remoterender");
    }

    private static boolean mappingSupports(RequestMappingInfo info, HttpMethod method) {
        var methods = info.getMethodsCondition().getMethods();
        if (method == HttpMethod.HEAD) {
            return methods.contains(org.springframework.web.bind.annotation.RequestMethod.GET)
                    || methods.contains(org.springframework.web.bind.annotation.RequestMethod.HEAD);
        }
        return methods.stream().anyMatch(candidate -> candidate.name().equals(method.name()));
    }

    private static String normalize(String pattern) {
        return pattern.replaceAll("\\{[^/}]+}", "*");
    }

    private static final int EXPECTED_W2_ROUTE_COUNT = 9;
}
