package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomPolicyPayloadParserTest {

    @Test
    void parsesCustomPayloadFromJson() {
        String json = """
                {
                  "tier": "CUSTOM",
                  "maxResolutionHeight": 1080,
                  "gpuAllowed": false,
                  "allowedProviders": ["javacv"],
                  "exportFormats": ["mp4"]
                }
                """;
        EntitlementPolicy policy = CustomPolicyPayloadParser.parse("tenant-1", "ovr-1", json);
        assertEquals("CUSTOM", policy.tier());
        assertEquals(1080, policy.maxResolutionHeight());
        assertTrue(!policy.gpuAllowed());
        assertTrue(policy.allowedProviders().contains("javacv"));
        assertTrue(policy.exportFormats().contains("mp4"));
        assertEquals("custom_policy_db", policy.extra().get("source"));
    }

    @Test
    void usesTierPresetWhenPayloadSpecifiesPro() {
        String json = "{\"tier\":\"PRO\"}";
        EntitlementPolicy policy = CustomPolicyPayloadParser.parse("tenant-2", null, json);
        assertEquals("PRO", policy.tier());
        assertEquals(1920, policy.maxResolutionWidth());
    }
}
