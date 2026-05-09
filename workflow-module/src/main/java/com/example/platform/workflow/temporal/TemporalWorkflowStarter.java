package com.example.platform.workflow.temporal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppTemporalProperties.class)
public class TemporalWorkflowStarter {
}