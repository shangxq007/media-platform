package com.example.platform.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the browser-readable runtime configuration as a classic script.
 *
 * <p>The OIDC authority and audience are deployment environment facts. Baking them into the
 * committed bundle at build time makes the shipped artifact environment-specific and forces the
 * local validation harness to commit test issuer values. This endpoint lets one reproducible
 * bundle serve every environment: the SPA reads {@code window.__MEDIA_PLATFORM_CONFIG__} before
 * its build-time {@code VITE_OIDC_*} fallback (see {@code frontend/src/auth/oidcConfig.ts}).
 *
 * <p>The payload carries no credentials, secrets, or tokens — only the issuer, audience/client id
 * and redirect origin that the browser must know to start an Authorization Code + PKCE flow.
 * The endpoint is public (the SPA cannot authenticate before it knows the authority) and is
 * covered by the shared {@code anyRequest().permitAll()} rule.
 */
@RestController
public class ClientConfigController {

    private final boolean oidcEnabled;
    private final String issuer;
    private final String audience;
    private final String redirectUri;
    private final String scope;

    public ClientConfigController(
            @Value("${app.security.oauth2.enabled:false}") boolean oidcEnabled,
            @Value("${app.security.oauth2.issuer-uri:}") String issuer,
            @Value("${app.security.oauth2.audience:}") String audience,
            @Value("${app.security.oauth2.redirect-uri:}") String redirectUri,
            @Value("${app.security.oauth2.scope:openid profile email}") String scope) {
        this.oidcEnabled = oidcEnabled;
        this.issuer = issuer == null ? "" : issuer.trim();
        this.audience = audience == null ? "" : audience.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.scope = scope == null || scope.isBlank() ? "openid profile email" : scope.trim();
    }

    @GetMapping("/config.js")
    public ResponseEntity<String> configJs() {
        Map<String, Object> oidc = new LinkedHashMap<>();
        oidc.put("enabled", oidcEnabled);
        oidc.put("issuer", issuer);
        oidc.put("clientId", audience);
        oidc.put("redirectUri", redirectUri);
        oidc.put("scope", scope);
        String body = "window.__MEDIA_PLATFORM_CONFIG__=" + toJson(Map.of("oidc", oidc)) + ";";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendMember(json, entry);
        }
        return json.append('}').toString();
    }

    private static void appendMember(StringBuilder json, Map.Entry<String, Object> entry) {
        json.append('"').append(escape(entry.getKey())).append("\":");
        Object value = entry.getValue();
        if (value instanceof Boolean || value instanceof Number) {
            json.append(value);
        } else if (value instanceof Map<?, ?> nested) {
            json.append(toJson(cast(nested)));
        } else {
            json.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> nested) {
        return (Map<String, Object>) nested;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
