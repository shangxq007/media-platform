package com.example.platform.sandbox.worker.config;

import com.example.platform.sandbox.LocalSandboxProcessExecutionAdapter;
import com.example.platform.sandbox.SandboxProcessExecutionPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SandboxWorkerProperties.class)
public class SandboxWorkerConfig {
    @Bean
    SandboxProcessExecutionPort sandboxProcessExecutionPort() {
        return new LocalSandboxProcessExecutionAdapter();
    }
}
