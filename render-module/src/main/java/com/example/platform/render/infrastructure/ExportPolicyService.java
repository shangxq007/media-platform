package com.example.platform.render.infrastructure;

import com.example.platform.render.app.clientexport.ClientExportPresetCatalog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for determining export tiers, presets, and provider routing based on user entitlements.
 */
@Service
public class ExportPolicyService implements ClientExportPresetCatalog {

    private static final Map<String, ExportTier> DEFAULT_TIERS = Map.of(
            "FREE", new ExportTier("FREE", 1, List.of("free_720p_watermarked"), true, false),
            "PRO", new ExportTier("PRO", 2, List.of("pro_1080p", "default_1080p", "default_720p", "social_720p"), false, false),
            "TEAM", new ExportTier("TEAM", 3, List.of("team_4k", "ofx_1080p", "ofx_720p", "pro_1080p", "default_1080p"), false, false),
            "ENTERPRISE", new ExportTier("ENTERPRISE", 4, List.of("enterprise_4k_ofx", "team_4k", "ofx_1080p", "pro_1080p", "default_1080p"), false, false),
            "EXPERIMENTAL", new ExportTier("EXPERIMENTAL", 5, List.of("experimental_all_providers"), false, true)
    );

    private static final Map<String, ExportPreset> DEFAULT_PRESETS = Map.ofEntries(
            Map.entry("client_720p_watermarked", new ExportPreset("client_720p_watermarked", "Free 720p (Browser)",
                    "1280x720", 30, "mp4", "h264", "aac", true, "FREE", "client")),
            Map.entry("free_720p_watermarked", new ExportPreset("free_720p_watermarked", "Free 720p (Watermarked)",
                    "1280x720", 30, "mp4", "h264", "aac", true, "FREE", null)),
            Map.entry("pro_1080p", new ExportPreset("pro_1080p", "Pro 1080p",
                    "1920x1080", 30, "mp4", "h264", "aac", false, "PRO", null)),
            Map.entry("team_4k", new ExportPreset("team_4k", "Team 4K",
                    "3840x2160", 30, "mp4", "h264", "aac", false, "TEAM", null)),
            Map.entry("enterprise_4k_ofx", new ExportPreset("enterprise_4k_ofx", "Enterprise 4K OFX",
                    "3840x2160", 30, "mp4", "h264", "aac", false, "ENTERPRISE", "ofx")),
            Map.entry("experimental_all_providers", new ExportPreset("experimental_all_providers", "Experimental (All Providers)",
                    "3840x2160", 60, "mp4", "h264", "aac", false, "EXPERIMENTAL", "ofx")),
            Map.entry("preview_720p", new ExportPreset("preview_720p", "Preview 720p",
                    "1280x720", 30, "mp4", "h264", "aac", false, "FREE", null)),
            Map.entry("hq_1080p", new ExportPreset("hq_1080p", "HQ 1080p",
                    "1920x1080", 30, "mp4", "h264", "aac", false, "PRO", null)),
            Map.entry("h265", new ExportPreset("h265", "H.265 1080p",
                    "1920x1080", 30, "mp4", "h265", "aac", false, "PRO", null)),
            Map.entry("vp9", new ExportPreset("vp9", "VP9 1080p",
                    "1920x1080", 30, "webm", "vp9", "opus", false, "PRO", null))
    );

    /**
     * Get the export tier for a user.
     */
    public ExportTier getTier(String tierName) {
        ExportTier tier = DEFAULT_TIERS.get(tierName);
        if (tier == null) {
            return DEFAULT_TIERS.get("FREE");
        }
        return tier;
    }

    /**
     * Get all available presets for a given tier.
     */
    public List<ExportPreset> getAvailablePresets(String tierName) {
        ExportTier tier = getTier(tierName);
        return tier.allowedPresets().stream()
                .map(DEFAULT_PRESETS::get)
                .filter(p -> p != null)
                .toList();
    }

    /**
     * Check if a preset is available for a tier.
     */
    public boolean isPresetAvailable(String presetName, String tierName) {
        ExportTier tier = getTier(tierName);
        return tier.allowedPresets().contains(presetName);
    }

    /**
     * Get a specific preset.
     */
    public ExportPreset getPreset(String presetName) {
        if (presetName == null) {
            return null;
        }
        return DEFAULT_PRESETS.getOrDefault(presetName,
                DEFAULT_PRESETS.get("free_720p_watermarked"));
    }

    @Override
    public Optional<ClientExportPresetCatalog.Preset> findPreset(String presetName) {
        return Optional.ofNullable(DEFAULT_PRESETS.get(presetName)).map(ExportPolicyService::toCatalogPreset);
    }

    @Override
    public List<ClientExportPresetCatalog.Preset> listPresets() {
        return DEFAULT_PRESETS.values().stream()
                .map(ExportPolicyService::toCatalogPreset)
                .sorted(java.util.Comparator.comparing(ClientExportPresetCatalog.Preset::name))
                .toList();
    }

    private static ClientExportPresetCatalog.Preset toCatalogPreset(ExportPreset preset) {
        return new ClientExportPresetCatalog.Preset(
                preset.name(), preset.displayName(), preset.resolution(), preset.frameRate(),
                preset.format(), preset.videoCodec(), preset.audioCodec(), preset.watermark(),
                preset.providerKey());
    }

    /**
     * Get the default preset for a tier.
     */
    public ExportPreset getDefaultPreset(String tierName) {
        List<ExportPreset> presets = getAvailablePresets(tierName);
        return presets.isEmpty() ? DEFAULT_PRESETS.get("free_720p_watermarked") : presets.get(0);
    }

    /**
     * Determine the provider key for a preset.
     */
    public String resolveProvider(String presetName, String tierName) {
        ExportPreset preset = getPreset(presetName);
        return preset != null ? preset.providerKey() : null;
    }

    /**
     * Whether the output should have a watermark.
     */
    public boolean requiresWatermark(String tierName) {
        return getTier(tierName).watermark();
    }

    /**
     * Whether experimental features are allowed.
     */
    public boolean isExperimentalAllowed(String tierName) {
        return getTier(tierName).experimental();
    }

    public record ExportTier(String name, int level, List<String> allowedPresets,
                              boolean watermark, boolean experimental) {}

    public record ExportPreset(String name, String displayName, String resolution,
                                int frameRate, String format, String videoCodec,
                                String audioCodec, boolean watermark, String requiredTier,
                                String providerKey) {
        public int width() {
            return Integer.parseInt(resolution.split("x")[0]);
        }
        public int height() {
            return Integer.parseInt(resolution.split("x")[1]);
        }
    }
}
