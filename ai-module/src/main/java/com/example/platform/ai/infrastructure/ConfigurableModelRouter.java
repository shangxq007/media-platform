package com.example.platform.ai.infrastructure;

import com.example.platform.ai.domain.ModelRouter;
import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableModelRouter implements ModelRouter {

    private final AiRoutingProperties properties;

    public ConfigurableModelRouter(AiRoutingProperties properties) {
        this.properties = properties;
    }

    @Override
    public RoutePlan routePlan(String capability) {
        AiRoutingProperties.CapabilityRouting routing = properties.getRouting().get(capability);
        if (routing != null && routing.getPrimary() != null && routing.getPrimary().getProvider() != null) {
            List<RouteTarget> targets = new ArrayList<>();
            targets.add(toTarget(routing.getPrimary()));
            if (routing.getFallback() != null) {
                for (AiRoutingProperties.RouteEndpoint endpoint : routing.getFallback()) {
                    if (endpoint != null && endpoint.getProvider() != null) {
                        targets.add(toTarget(endpoint));
                    }
                }
            }
            return new RoutePlan(targets);
        }
        return new RoutePlan(List.of(new RouteTarget(properties.getDefaultProvider())));
    }

    private static RouteTarget toTarget(AiRoutingProperties.RouteEndpoint endpoint) {
        String model = endpoint.getModel();
        if (model == null || model.isBlank()) {
            return new RouteTarget(endpoint.getProvider());
        }
        return new RouteTarget(endpoint.getProvider(), model.trim());
    }
}
