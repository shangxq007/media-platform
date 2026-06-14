package com.example.platform.render.infrastructure.anchor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemAnchorFunctionConfiguration {

    @Bean
    public SystemAnchorFunction systemAnchorFunction() {
        return SystemAnchorFunction.createDefault();
    }
}
