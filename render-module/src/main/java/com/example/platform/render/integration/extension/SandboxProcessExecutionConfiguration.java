package com.example.platform.render.integration.extension;

import com.example.platform.sandbox.LocalSandboxProcessExecutionAdapter;
import com.example.platform.sandbox.SandboxProcessExecutionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Framework composition for the technology-neutral local sandbox process port. */
@Configuration(proxyBeanMethods = false)
public class SandboxProcessExecutionConfiguration {

    @Bean
    SandboxProcessExecutionPort sandboxProcessExecutionPort() {
        return new LocalSandboxProcessExecutionAdapter();
    }
}
