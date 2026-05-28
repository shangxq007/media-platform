package com.example.platform.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.platform.production.EgressProxySmokeService.SmokeResult;
import com.example.platform.production.EgressProxySmokeService.SmokeResult.Status;
import org.junit.jupiter.api.Test;

class EgressProxySmokeServiceTest {

    private static EgressProxySmokeProperties props(boolean enabled, String url) {
        EgressProxySmokeProperties p = new EgressProxySmokeProperties();
        p.getSmoke().setEnabled(enabled);
        p.getSmoke().setUrl(url);
        p.getSmoke().setTimeoutMs(1000);
        p.getSmoke().setExpectedStatus(200);
        return p;
    }

    @Test
    void disabledReturnsDisabled() {
        var service = new EgressProxySmokeService(props(false, ""));
        SmokeResult result = service.execute();
        assertEquals(Status.DISABLED, result.status());
        assertNull(result.targetHost());
        assertNull(result.error());
    }

    @Test
    void enabledWithBlankUrlReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, ""));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
        assertNotNull(result.error());
    }

    @Test
    void enabledWithNullUrlReturnsConfigError() {
        EgressProxySmokeProperties p = props(true, null);
        var service = new EgressProxySmokeService(p);
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
    }

    @Test
    void enabledWithInvalidSchemeReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "ftp://example.com"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
        assertNotNull(result.error());
    }

    @Test
    void enabledWithUserinfoReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "https://user:pass@example.com"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
        assertNotNull(result.error());
    }

    @Test
    void enabledWithMetadataIpReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "http://169.254.169.254/latest/meta-data"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
        assertNotNull(result.error());
    }

    @Test
    void enabledWithLocalhostReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "http://localhost:8080/health"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
    }

    @Test
    void enabledWithLoopbackReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "http://127.0.0.1/health"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
    }

    @Test
    void enabledWithNoHostReturnsConfigError() {
        var service = new EgressProxySmokeService(props(true, "https://"));
        SmokeResult result = service.execute();
        assertEquals(Status.CONFIG_ERROR, result.status());
    }

    @Test
    void connectionRefusedReturnsFailed() {
        // Port 1 is almost certainly not listening
        var service = new EgressProxySmokeService(props(true, "http://127.0.0.2:1/"));
        SmokeResult result = service.execute();
        assertEquals(Status.FAILED, result.status());
        assertNotNull(result.error());
    }

    @Test
    void resultDoesNotIncludeResponseBody() {
        var service = new EgressProxySmokeService(props(true, "http://127.0.0.2:1/"));
        SmokeResult result = service.execute();
        var details = result.toHealthDetails();
        assertNull(details.get("body"));
        assertNull(details.get("responseBody"));
    }

    @Test
    void resultDoesNotIncludeFullUrl() {
        var service = new EgressProxySmokeService(props(true, "http://127.0.0.2:1/path?secret=abc"));
        SmokeResult result = service.execute();
        var details = result.toHealthDetails();
        assertNull(details.get("url"));
        assertNull(details.get("targetUrl"));
        // targetHost should only be the host, not path/query
        if (details.containsKey("targetHost")) {
            String host = (String) details.get("targetHost");
            // host should be just the hostname, not include path or query
            assertFalse(host.contains("path"));
            assertFalse(host.contains("secret"));
        }
    }

    @Test
    void toHealthDetailsDisabledStatus() {
        SmokeResult result = SmokeResult.disabled();
        var details = result.toHealthDetails();
        assertEquals("DISABLED", details.get("status"));
        assertEquals(1, details.size());
    }

    @Test
    void toHealthDetailsSuccessStatus() {
        SmokeResult result = SmokeResult.success("example.com", 200, 150);
        var details = result.toHealthDetails();
        assertEquals("SUCCESS", details.get("status"));
        assertEquals("example.com", details.get("targetHost"));
        assertEquals(200, details.get("statusCode"));
        assertEquals(150L, details.get("durationMs"));
        assertNull(details.get("error"));
    }

    @Test
    void toHealthDetailsFailedStatus() {
        SmokeResult result = SmokeResult.failed("example.com", 503, 200, "unexpected status");
        var details = result.toHealthDetails();
        assertEquals("FAILED", details.get("status"));
        assertEquals("example.com", details.get("targetHost"));
        assertEquals(503, details.get("statusCode"));
        assertEquals("unexpected status", details.get("error"));
    }

    @Test
    void toHealthDetailsConfigErrorStatus() {
        SmokeResult result = SmokeResult.configError("bad url");
        var details = result.toHealthDetails();
        assertEquals("CONFIG_ERROR", details.get("status"));
        assertEquals("bad url", details.get("error"));
        assertNull(details.get("targetHost"));
    }
}
