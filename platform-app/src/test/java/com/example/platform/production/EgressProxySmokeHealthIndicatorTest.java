package com.example.platform.production;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class EgressProxySmokeHealthIndicatorTest {

    private static EgressProxySmokeProperties disabledProps() {
        EgressProxySmokeProperties p = new EgressProxySmokeProperties();
        p.getSmoke().setEnabled(false);
        return p;
    }

    private static EgressProxySmokeProperties enabledProps(String url) {
        EgressProxySmokeProperties p = new EgressProxySmokeProperties();
        p.getSmoke().setEnabled(true);
        p.getSmoke().setUrl(url);
        p.getSmoke().setTimeoutMs(1000);
        return p;
    }

    @Test
    void disabledReturnsUp() {
        var service = new EgressProxySmokeService(disabledProps());
        var indicator = new EgressProxySmokeHealthIndicator(service);
        var health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void configErrorReturnsDown() {
        var service = new EgressProxySmokeService(enabledProps(""));
        var indicator = new EgressProxySmokeHealthIndicator(service);
        var health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void connectionFailedReturnsDown() {
        var service = new EgressProxySmokeService(enabledProps("http://127.0.0.2:1/"));
        var indicator = new EgressProxySmokeHealthIndicator(service);
        var health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void metadataIpReturnsDown() {
        var service = new EgressProxySmokeService(enabledProps("http://169.254.169.254/latest"));
        var indicator = new EgressProxySmokeHealthIndicator(service);
        var health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }
}
