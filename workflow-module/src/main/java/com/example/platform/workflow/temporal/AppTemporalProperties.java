package com.example.platform.workflow.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.temporal")
public record AppTemporalProperties(boolean enabled) {}