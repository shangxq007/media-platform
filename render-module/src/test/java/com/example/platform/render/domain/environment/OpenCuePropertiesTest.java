package com.example.platform.render.domain.environment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenCuePropertiesTest {

    private OpenCueProperties props;

    @BeforeEach
    void setUp() {
        props = new OpenCueProperties();
    }

    @Test
    void disabledByDefault() {
        assertFalse(props.isEnabled(), "OpenCue must be disabled by default");
    }

    @Test
    void networkSubmitDisabledByDefault() {
        assertFalse(props.isAllowNetworkSubmit(), "network submit must be disabled by default");
    }

    @Test
    void productionSubmitDisabledByDefault() {
        assertFalse(props.isProductionSubmitEnabled(), "production submit must be disabled by default");
    }

    @Test
    void stubModeEnabledByDefault() {
        assertTrue(props.isStubModeEnabled(), "stub mode must be enabled by default");
    }

    @Test
    void safeServerDefault() {
        assertEquals("localhost", props.getServer());
    }

    @Test
    void grpcPortDefaultIsBounded() {
        assertTrue(props.getGrpcPort() > 0 && props.getGrpcPort() < 65536);
    }

    @Test
    void timeoutDefaultsArePositive() {
        assertTrue(props.getTimeoutSec() > 0, "timeoutSec must be positive");
        assertTrue(props.getSubmitTimeoutSec() > 0, "submitTimeoutSec must be positive");
        assertTrue(props.getStatusTimeoutSec() > 0, "statusTimeoutSec must be positive");
        assertTrue(props.getCancelTimeoutSec() > 0, "cancelTimeoutSec must be positive");
    }

    @Test
    void maxLayersIsBounded() {
        assertTrue(props.getMaxLayers() > 0 && props.getMaxLayers() <= 1024);
    }

    @Test
    void maxCommandsPerLayerIsBounded() {
        assertTrue(props.getMaxCommandsPerLayer() > 0 && props.getMaxCommandsPerLayer() <= 1024);
    }

    @Test
    void maxEnvVarsIsBounded() {
        assertTrue(props.getMaxEnvironmentVariables() > 0 && props.getMaxEnvironmentVariables() <= 256);
    }

    @Test
    void maxTagsIsBounded() {
        assertTrue(props.getMaxTags() > 0 && props.getMaxTags() <= 128);
    }

    @Test
    void priorityRangeIsValid() {
        assertTrue(props.getMinPriority() >= 0);
        assertTrue(props.getMaxPriority() > props.getMinPriority());
    }

    @Test
    void defaultOwnerIsSet() {
        assertNotNull(props.getDefaultOwner());
        assertFalse(props.getDefaultOwner().isBlank());
    }

    @Test
    void defaultPriorityInRange() {
        assertTrue(props.getDefaultPriority() >= props.getMinPriority());
        assertTrue(props.getDefaultPriority() <= props.getMaxPriority());
    }

    @Test
    void allBoolFlagsFailClosedByDefault() {
        assertFalse(props.isEnabled());
        assertFalse(props.isAllowNetworkSubmit());
        assertFalse(props.isProductionSubmitEnabled());
    }
}
