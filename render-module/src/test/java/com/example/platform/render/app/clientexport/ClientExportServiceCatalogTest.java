package com.example.platform.render.app.clientexport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.render.app.clientexport.ClientExportPresetCatalog.Preset;
import com.example.platform.render.infrastructure.clientexport.ClientExportSessionRepository;
import com.example.platform.shared.commercial.CommercialDecision;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientExportServiceCatalogTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void availablePresetsUseNeutralCatalogAndAdmissionInsteadOfTierLabel() {
        Preset browser = preset("client_720p_watermarked", true, "client");
        Preset free = preset("free_720p_watermarked", true, null);
        Preset denied = preset("team_4k", false, null);
        ClientExportPresetCatalog catalog = new ClientExportPresetCatalog() {
            private final List<Preset> presets = List.of(browser, free, denied);

            @Override
            public Optional<Preset> findPreset(String presetName) {
                return presets.stream().filter(preset -> preset.name().equals(presetName)).findFirst();
            }

            @Override
            public List<Preset> listPresets() {
                return presets;
            }
        };
        var admission = (com.example.platform.shared.commercial.CommercialAdmissionPort) request -> {
            boolean allowed = !request.entitlementKey().endsWith("team_4k");
            return new CommercialDecision(
                    request.principal(), request.action(), allowed,
                    allowed ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.NOT_ENTITLED,
                    List.of(), "test-v1", request.traceId(), request.decidedAt());
        };
        var service = new ClientExportService(
                tempDir.toString(), mock(ClientExportSessionRepository.class), catalog, admission);

        var config = service.createSessionWithConfig(
                "tenant-1", "workspace-1", "project-1", "user-1",
                "UNRECOGNIZED_PRESENTATION_TIER", null, null);

        assertTrue(config.availablePresets().stream()
                .anyMatch(preset -> preset.get("name").equals("free_720p_watermarked")));
        assertFalse(config.availablePresets().stream()
                .anyMatch(preset -> preset.get("name").equals("team_4k")));
    }

    private static Preset preset(String name, boolean watermark, String providerKey) {
        return new Preset(
                name, name, name.equals("team_4k") ? "3840x2160" : "1280x720",
                30, "mp4", "h264", "aac", watermark, providerKey);
    }
}
