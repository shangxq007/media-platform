package com.example.platform.render.infrastructure;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RenderProviderRouter {
    private final List<RenderProvider> providers;

    public RenderProviderRouter(List<RenderProvider> providers) {
        this.providers = providers;
    }

    public RenderProvider route(String profile) {
        return providers.stream()
                .filter(p -> p.getSupportedProfiles().contains(profile))
                .findFirst()
                .orElse(providers.isEmpty() ? null : providers.get(0));
    }
}
