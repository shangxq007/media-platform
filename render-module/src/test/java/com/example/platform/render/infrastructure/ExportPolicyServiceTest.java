package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExportPolicyServiceTest {

    private final ExportPolicyService service = new ExportPolicyService();

    @Test
    void formerConcreteProviderPresetsRemainUnresolvedUntilTypedPluginBinding() {
        List<String> unresolvedPresets = List.of(
                "free_720p_watermarked", "pro_1080p", "team_4k",
                "preview_720p", "hq_1080p", "h265", "vp9");

        unresolvedPresets.forEach(presetName ->
                assertNull(service.getPreset(presetName).providerKey(), presetName));
    }

    @Test
    void explicitIndependentProviderPresetsRemainResolved() {
        assertEquals("client", service.getPreset("client_720p_watermarked").providerKey());
        assertEquals("ofx", service.getPreset("enterprise_4k_ofx").providerKey());
        assertEquals("ofx", service.getPreset("experimental_all_providers").providerKey());

        assertEquals("client", service.resolveProvider("client_720p_watermarked", "FREE"));
        assertEquals("ofx", service.resolveProvider("enterprise_4k_ofx", "ENTERPRISE"));
    }

    @Test
    void unresolvedProviderResolutionReturnsNullAndNeverSymbolicIdentity() {
        List<String> unresolvedPresets = List.of(
                "free_720p_watermarked", "pro_1080p", "team_4k",
                "preview_720p", "hq_1080p", "h265", "vp9", "missing");

        unresolvedPresets.forEach(presetName -> {
            String resolved = service.resolveProvider(presetName, "TEAM");
            assertNull(resolved, presetName);
            assertFalse("provider".equals(resolved), presetName);
        });
        assertNull(service.resolveProvider(null, "FREE"));
    }
}
