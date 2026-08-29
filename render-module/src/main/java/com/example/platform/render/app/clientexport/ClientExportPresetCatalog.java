package com.example.platform.render.app.clientexport;

import java.util.List;
import java.util.Optional;

/** Neutral technical catalog of client-export presets, independent of commercial plans or tiers. */
public interface ClientExportPresetCatalog {

    Optional<Preset> findPreset(String presetName);

    List<Preset> listPresets();

    record Preset(
            String name,
            String displayName,
            String resolution,
            int frameRate,
            String format,
            String videoCodec,
            String audioCodec,
            boolean watermark,
            String providerKey) {
        public int width() {
            return Integer.parseInt(resolution.split("x")[0]);
        }

        public int height() {
            return Integer.parseInt(resolution.split("x")[1]);
        }
    }
}
