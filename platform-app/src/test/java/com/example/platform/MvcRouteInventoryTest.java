package com.example.platform;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
class MvcRouteInventoryTest {

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
    }
}
