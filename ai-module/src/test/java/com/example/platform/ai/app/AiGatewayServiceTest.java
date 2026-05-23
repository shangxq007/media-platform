package com.example.platform.ai.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.ai.domain.ModelRouter;
import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiGatewayServiceTest {

    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        ModelRouter router = capability -> new RoutePlan(List.of(new RouteTarget("testProvider")));
        Map<String, ChatProvider> providers = new HashMap<>();
        providers.put("testProvider", request -> new ChatResult("testProvider", "test-model", "response"));
        providers.put("failingProvider", request -> {
            throw new RuntimeException("provider error");
        });
        service = new AiGatewayService(router, providers);
    }

    @Test
    void chatRoutesToCorrectProvider() {
        ChatResult result = service.chat("summarize", "test prompt");
        assertNotNull(result);
        assertEquals("testProvider", result.provider());
        assertEquals("test-model", result.model());
        assertEquals("response", result.content());
    }

    @Test
    void chatThrowsWhenProviderNotFound() {
        ModelRouter router = capability -> new RoutePlan(List.of(new RouteTarget("nonExistentProvider")));
        Map<String, ChatProvider> providers = new HashMap<>();
        AiGatewayService svc = new AiGatewayService(router, providers);

        assertThrows(IllegalStateException.class, () -> svc.chat("cap", "prompt"));
    }

    @Test
    void chatPropagatesProviderException() {
        ModelRouter router = capability -> new RoutePlan(List.of(new RouteTarget("failingProvider")));
        Map<String, ChatProvider> providers = new HashMap<>();
        providers.put("failingProvider", request -> {
            throw new RuntimeException("provider error");
        });
        AiGatewayService svc = new AiGatewayService(router, providers);

        assertThrows(RuntimeException.class, () -> svc.chat("cap", "prompt"));
    }

    @Test
    void chatPassesRequestToProvider() {
        final ChatRequest[] captured = new ChatRequest[1];
        ModelRouter router = capability -> new RoutePlan(List.of(new RouteTarget("capturingProvider", "test-model")));
        Map<String, ChatProvider> providers = new HashMap<>();
        providers.put("capturingProvider", request -> {
            captured[0] = request;
            return new ChatResult("capturingProvider", "model", "ok");
        });
        AiGatewayService svc = new AiGatewayService(router, providers);

        svc.chat("myCapability", "myPrompt");

        assertNotNull(captured[0]);
        assertEquals("myCapability", captured[0].capability());
        assertEquals("myPrompt", captured[0].prompt());
    }
}
