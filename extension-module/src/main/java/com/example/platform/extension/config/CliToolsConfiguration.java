package com.example.platform.extension.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CliToolsProperties.class)
public class CliToolsConfiguration {
}
