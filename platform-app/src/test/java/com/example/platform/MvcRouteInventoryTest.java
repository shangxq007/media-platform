package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.security.PhaseZeroContainmentPolicy;
import com.example.platform.security.RuntimeMvcRouteDiscovery;
import com.example.platform.security.RuntimeRoutePolicyVerifier;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
class MvcRouteInventoryTest extends PostgresTestContainerSupport {

    @Autowired
    private RuntimeMvcRouteDiscovery routeDiscovery;

    @Autowired
    private RuntimeRoutePolicyVerifier routePolicyVerifier;

    @Test
    void liveApplicationRouteUniverseIsNonEmptyAndCompletelyClassified() throws Exception {
        var discovered = routeDiscovery.discoverApplicationRoutes();
        assertFalse(discovered.isEmpty(),
                "an empty RequestMapping universe cannot establish containment completeness");

        var report = routePolicyVerifier.verify();
        assertEquals(discovered, report.routes());
        assertEquals(report.routeCount(),
                report.classificationCounts().values().stream().mapToLong(Long::longValue).sum());

        for (PhaseZeroContainmentPolicy.Classification classification
                : PhaseZeroContainmentPolicy.Classification.values()) {
            assertTrue(report.classificationCounts().get(classification) > 0,
                    () -> "runtime inventory does not exercise classification " + classification);
        }

        StringBuilder inventory = new StringBuilder()
                .append("ROUTE_COUNT=").append(report.routeCount()).append('\n')
                .append("CLASSIFICATION_COUNTS=").append(report.classificationCounts()).append('\n');
        report.routes().forEach(route -> inventory
                .append(PhaseZeroContainmentPolicy.classify(route.method(), route.path()).orElseThrow())
                .append(" | ")
                .append(route.displayName())
                .append('\n'));
        Files.writeString(Path.of("/tmp/mvc-route-policy-inventory.txt"), inventory.toString());
    }
}
