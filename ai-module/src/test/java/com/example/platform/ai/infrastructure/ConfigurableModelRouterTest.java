package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurableModelRouterTest {

    @Test
    void usesCapabilityPrimaryAndFallback() {
        AiRoutingProperties props = new AiRoutingProperties();
        AiRoutingProperties.CapabilityRouting routing = new AiRoutingProperties.CapabilityRouting();
        AiRoutingProperties.RouteEndpoint primary = new AiRoutingProperties.RouteEndpoint();
        primary.setProvider("openAiChatProvider");
        primary.setModel("gpt-4o-mini");
        routing.setPrimary(primary);
        AiRoutingProperties.RouteEndpoint fallback = new AiRoutingProperties.RouteEndpoint();
        fallback.setProvider("stubChatProvider");
        routing.setFallback(List.of(fallback));
        props.setRouting(Map.of("script-generation", routing));

        ConfigurableModelRouter router = new ConfigurableModelRouter(props);
        RoutePlan plan = router.routePlan("script-generation");

        assertEquals(2, plan.targets().size());
        assertEquals(new RouteTarget("openAiChatProvider", "gpt-4o-mini"), plan.targets().get(0));
        assertEquals(new RouteTarget("stubChatProvider"), plan.targets().get(1));
    }

    @Test
    void fallsBackToDefaultProviderForUnknownCapability() {
        AiRoutingProperties props = new AiRoutingProperties();
        props.setDefaultProvider("stubChatProvider");

        ConfigurableModelRouter router = new ConfigurableModelRouter(props);
        RoutePlan plan = router.routePlan("unknown-cap");

        assertEquals(1, plan.targets().size());
        assertEquals("stubChatProvider", plan.primary().providerId());
    }
}
