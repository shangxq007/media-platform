package com.example.platform.sandbox;

import com.example.platform.sandbox.app.SandboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class SandboxConfiguration {
}
