package com.example.platform.entitlement.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an export should run in the browser (CLIENT) or on the server (SERVER).
 */
public final class ClientExportRoutingPolicy {

    public static final String LOCATION_CLIENT = "CLIENT";
    public static final String LOCATION_SERVER = "SERVER";

    /** Max timeline duration for in-browser export (seconds). */
    public static final long MAX_CLIENT_DURATION_SECONDS = 300L;

    public static final String PRESET_CLIENT_720P = "client_720p_watermarked";

    private static final Set<String> CLIENT_CAPABLE_PRESETS = Set.of(
            "free_720p_watermarked",
            PRESET_CLIENT_720P,
            "preview_720p",
            "mobile_480p",
            "default_720p");

    private static final Set<String> SERVER_ONLY_EFFECT_PREFIXES = Set.of(
            "video.natron",
            "video.ofx",
            "gpu.",
            "remote-");

    private ClientExportRoutingPolicy() {}

    public record RoutingDecision(
            String recommendedRenderLocation,
            boolean clientExportSupported,
            List<String> unsupportedReasons) {}

    public static RoutingDecision resolve(
            String tier,
            String preset,
            long estimatedDurationSeconds,
            List<String> effectKeys,
            boolean clientExportFeatureEnabled) {

        List<String> unsupported = new ArrayList<>();
        String normalizedTier = tier != null ? tier.toUpperCase(Locale.ROOT) : "FREE";
        String normalizedPreset = preset != null ? preset.toLowerCase(Locale.ROOT) : "";

        if (!clientExportFeatureEnabled) {
            unsupported.add("CLIENT_EXPORT_FEATURE_DISABLED");
            return new RoutingDecision(LOCATION_SERVER, false, List.copyOf(unsupported));
        }

        if (normalizedPreset.startsWith("gpu_")
                || normalizedPreset.contains("4k")
                || normalizedPreset.contains("2160p")
                || normalizedPreset.startsWith("ofx_")
                || normalizedPreset.contains("enterprise")) {
            unsupported.add("PRESET_REQUIRES_SERVER");
            return new RoutingDecision(LOCATION_SERVER, false, List.copyOf(unsupported));
        }

        if (!CLIENT_CAPABLE_PRESETS.contains(normalizedPreset)) {
            unsupported.add("PRESET_NOT_CLIENT_CAPABLE");
        }

        if (estimatedDurationSeconds > MAX_CLIENT_DURATION_SECONDS) {
            unsupported.add("DURATION_EXCEEDS_CLIENT_LIMIT");
        }

        if (effectKeys != null) {
            for (String key : effectKeys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                String lower = key.toLowerCase(Locale.ROOT);
                for (String prefix : SERVER_ONLY_EFFECT_PREFIXES) {
                    if (lower.startsWith(prefix)) {
                        unsupported.add("EFFECT_REQUIRES_SERVER:" + key);
                        break;
                    }
                }
            }
        }

        if (!"FREE".equals(normalizedTier) && !PRESET_CLIENT_720P.equals(normalizedPreset)) {
            unsupported.add("TIER_USES_SERVER_EXPORT");
        }

        if (!unsupported.isEmpty()) {
            return new RoutingDecision(LOCATION_SERVER, false, List.copyOf(unsupported));
        }

        return new RoutingDecision(LOCATION_CLIENT, true, List.of());
    }

    public static List<String> parseEffectKeysFromTimelineJson(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        int idx = 0;
        String marker = "\"effectKey\"";
        while ((idx = timelineJson.indexOf(marker, idx)) >= 0) {
            int colon = timelineJson.indexOf(':', idx);
            int q1 = timelineJson.indexOf('"', colon + 1);
            int q2 = timelineJson.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) {
                keys.add(timelineJson.substring(q1 + 1, q2));
            }
            idx = q2 + 1;
        }
        marker = "\"effects\"";
        idx = 0;
        while ((idx = timelineJson.indexOf(marker, idx)) >= 0) {
            int bracket = timelineJson.indexOf('[', idx);
            int end = timelineJson.indexOf(']', bracket);
            if (bracket > 0 && end > bracket) {
                String slice = timelineJson.substring(bracket, end);
                int k = 0;
                while ((k = slice.indexOf('"', k)) >= 0) {
                    int k2 = slice.indexOf('"', k + 1);
                    if (k2 > k) {
                        String val = slice.substring(k + 1, k2);
                        if (val.startsWith("video.") || val.startsWith("audio.")) {
                            keys.add(val);
                        }
                    }
                    k = k2 + 1;
                }
            }
            idx = end + 1;
        }
        return keys.stream().distinct().toList();
    }
}
