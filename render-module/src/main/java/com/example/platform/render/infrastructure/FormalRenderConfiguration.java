package com.example.platform.render.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Independent Timeline asset GC remains available; legacy provider/cache/queue configuration is retired. */
@Configuration
@EnableConfigurationProperties(TimelineAssetGcProperties.class)
public class FormalRenderConfiguration {}
