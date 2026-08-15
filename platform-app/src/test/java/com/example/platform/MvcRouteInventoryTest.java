package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.*;
import java.util.*;
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

    private static final int EXPECTED_W2_ROUTE_COUNT = 9;
}
