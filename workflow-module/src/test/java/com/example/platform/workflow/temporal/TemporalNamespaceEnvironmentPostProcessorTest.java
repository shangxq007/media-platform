package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class TemporalNamespaceEnvironmentPostProcessorTest {

    private final TemporalNamespaceEnvironmentPostProcessor processor = new TemporalNamespaceEnvironmentPostProcessor();

    @Test
    void setsNamespaceWhenTemporalEnabledAndUnset() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.temporal.enabled", "true");
        env.setProperty("PLATFORM_ENV", "staging");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("media-platform-staging", env.getProperty("spring.temporal.namespace"));
    }

    @Test
    void doesNotOverrideExplicitSpringNamespace() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.temporal.enabled", "true");
        env.setProperty("spring.temporal.namespace", "custom");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("custom", env.getProperty("spring.temporal.namespace"));
    }

    @Test
    void skipsWhenTemporalDisabled() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.temporal.enabled", "false");
        env.setProperty("PLATFORM_ENV", "prod");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals(null, env.getProperty("spring.temporal.namespace"));
    }
}
