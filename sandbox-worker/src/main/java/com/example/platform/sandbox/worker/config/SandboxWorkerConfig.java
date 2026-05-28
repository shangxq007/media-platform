package com.example.platform.sandbox.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SandboxWorkerProperties.class)
public class SandboxWorkerConfig {
}
