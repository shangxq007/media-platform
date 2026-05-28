package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityHeadersTest {

    @Test
    void cspDirectivesShouldRestrictDefaultSrcToSelf() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"),
                "CSP should restrict default-src to 'self'");
    }

    @Test
    void cspDirectivesShouldRestrictObjectSrcToNone() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("object-src 'none'"),
                "CSP should set object-src to 'none'");
    }

    @Test
    void cspDirectivesShouldRestrictFrameAncestors() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("frame-ancestors 'none'"),
                "CSP should set frame-ancestors to 'none'");
    }

    @Test
    void cspDirectivesShouldRestrictBaseUriToSelf() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("base-uri 'self'"),
                "CSP should restrict base-uri to 'self'");
    }

    @Test
    void cspDirectivesShouldRestrictFormActionToSelf() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("form-action 'self'"),
                "CSP should restrict form-action to 'self'");
    }

    @Test
    void cspDirectivesShouldAllowBlobAndHttpsForImg() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("img-src 'self' data: blob: https:"),
                "CSP img-src should allow self, data:, blob:, and https:");
    }

    @Test
    void cspDirectivesShouldAllowBlobAndHttpsForMedia() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("media-src 'self' blob: data: https:"),
                "CSP media-src should allow self, blob:, data:, and https: for client export and media playback");
    }

    @Test
    void cspDirectivesShouldAllowWorkerAndChildBlob() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("worker-src 'self' blob:"),
                "CSP worker-src should allow self and blob: for web workers");
        assertTrue(csp.contains("child-src 'self' blob:"),
                "CSP child-src should allow self and blob:");
    }

    @Test
    void cspDirectivesShouldNotContainUnsafeEval() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(!csp.contains("'unsafe-eval'"),
                "CSP must NOT contain 'unsafe-eval'; new Function() has been removed from main.ts");
    }

    @Test
    void cspDirectivesShouldAllowUnsafeInlineForStyleOnly() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"),
                "CSP style-src should allow 'unsafe-inline' for Vue runtime style injection");
        assertTrue(!csp.contains("script-src") || !csp.substring(csp.indexOf("script-src")).contains("'unsafe-inline'"),
                "CSP script-src must NOT contain 'unsafe-inline'");
    }

    @Test
    void cspDirectivesShouldNotAllowWildcardConnectSrc() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        String connectSrc = extractDirective(csp, "connect-src");
        assertNotNull(connectSrc, "CSP should contain connect-src directive");
        assertTrue(!connectSrc.contains("*"),
                "CSP connect-src must NOT use wildcard; current: " + connectSrc);
    }

    @Test
    void cspDirectivesShouldAllowWssForConnect() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("connect-src 'self' https: wss:"),
                "CSP connect-src should allow self, https:, and wss: for API and WebSocket");
    }

    @Test
    void cspDirectivesShouldAllowDataAndSelfForFont() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("font-src 'self' data:"),
                "CSP font-src should allow self and data: for icon fonts");
    }

    @Test
    void cspDirectivesShouldRestrictManifestToSelf() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        assertTrue(csp.contains("manifest-src 'self'"),
                "CSP manifest-src should be restricted to self");
    }

    @Test
    void cspDirectivesShouldNotUseWildcardDefault() {
        String csp = SecurityFilterChainConfig.buildCspDirectives();
        String[] directives = csp.split(";");
        for (String directive : directives) {
            String trimmed = directive.trim();
            if (trimmed.contains("*") && !trimmed.startsWith("img-src") && !trimmed.startsWith("font-src")
                    && !trimmed.startsWith("media-src")) {
                assertEquals(-1, trimmed.indexOf("default-src *"),
                        "CSP default-src must NOT use wildcard");
                assertEquals(-1, trimmed.indexOf("script-src *"),
                        "CSP script-src must NOT use wildcard");
                assertEquals(-1, trimmed.indexOf("connect-src *"),
                        "CSP connect-src must NOT use wildcard");
                assertEquals(-1, trimmed.indexOf("style-src *"),
                        "CSP style-src must NOT use wildcard");
            }
        }
    }

    private static String extractDirective(String csp, String directiveName) {
        int start = csp.indexOf(directiveName);
        if (start < 0) return null;
        int end = csp.indexOf(";", start);
        if (end < 0) end = csp.length();
        return csp.substring(start, end).trim();
    }
}
