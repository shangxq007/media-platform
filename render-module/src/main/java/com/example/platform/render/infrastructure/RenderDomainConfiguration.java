package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.RenderJobStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RenderDomainConfiguration {

    @Bean
    public RenderJobStateMachine renderJobStateMachine() {
        return new RenderJobStateMachine();
    }
}
