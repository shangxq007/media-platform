package com.example.platform.render.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers")
public record RenderProviderProperties(
    ProviderConfig javacv,
    ProviderConfig ofx,
    ProviderConfig mock,
    ProviderConfig ffmpeg,
    ProviderConfig gstreamer,
    ProviderConfig gpac,
    ProviderConfig mlt
) {
    public record ProviderConfig(boolean enabled) {}
}
