package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TemporalNamespaceResolverTest {

    @Test
    void resolvesMediaPlatformEnvWhenUnset() {
        AppTemporalProperties props = new AppTemporalProperties();
        assertEquals("media-platform-prod", TemporalNamespaceResolver.resolve(props, null, "prod"));
    }

    @Test
    void temporalNamespaceEnvOverridesPrefix() {
        AppTemporalProperties props = new AppTemporalProperties();
        assertEquals("custom-ns", TemporalNamespaceResolver.resolve(props, "custom-ns", "prod"));
    }

    @Test
    void explicitAppNamespaceOverridesEnv() {
        AppTemporalProperties props = new AppTemporalProperties();
        props.setNamespace("media-platform-staging");
        assertEquals("media-platform-staging", TemporalNamespaceResolver.resolve(props, "ignored", "prod"));
    }

    @Test
    void appTemporalEnvironmentOverridesPlatformEnv() {
        AppTemporalProperties props = new AppTemporalProperties();
        props.setEnvironment("staging");
        assertEquals("media-platform-staging", TemporalNamespaceResolver.resolve(props, null, "prod"));
    }
}
