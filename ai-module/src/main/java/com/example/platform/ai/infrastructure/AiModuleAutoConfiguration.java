package com.example.platform.ai.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiRoutingProperties.class)
public class AiModuleAutoConfiguration {}
