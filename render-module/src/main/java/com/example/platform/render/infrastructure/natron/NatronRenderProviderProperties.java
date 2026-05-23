package com.example.platform.render.infrastructure.natron;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Natron render worker POC ({@code render.providers.natron}).
 */
@ConfigurationProperties(prefix = "render.providers.natron")
public class NatronRenderProviderProperties {

    /** When false, {@link NatronRenderProvider} bean is not created. */
    private boolean enabled = false;

    /** Primary POC effect key (backward compatible). */
    private String pocEffectKey = "video.natron_vignette";

    /** All Natron-routed effect keys with batch/FFmpeg templates. */
    private List<String> supportedEffectKeys = new ArrayList<>(List.of(
            "video.natron_vignette",
            "video.natron_color_grade"));

    /** Absolute path to {@code poc-render.sh}; empty → extract from classpath at runtime. */
    private String pocScriptPath = "";

    /** Natron headless binary name or path (validated when fallback is disabled). */
    private String rendererBinary = "NatronRenderer";

    /**
     * When true, {@code poc-render.sh} uses FFmpeg vignette even if NatronRenderer exists.
     * Useful for CI and local dev without Natron installed.
     */
    private boolean fallbackToFfmpeg = true;

    private long timeoutMillis = 600_000L;

    /** Reader node script name in batch_vignette.py (must match -i argument). */
    private String readerNodeName = "MyReader";

    /** Writer node script name in batch_vignette.py (must match -w argument). */
    private String writerNodeName = "MyWriter";

    /** Classpath template for Python batch script (Phase 2). */
    private String batchScriptTemplate = NatronBatchScriptGenerator.TEMPLATE_CLASSPATH;

    /** Auto-upgrade render profile when timeline uses Natron effects (orchestrator). */
    private boolean autoSelectProfile = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPocEffectKey() {
        return pocEffectKey;
    }

    public void setPocEffectKey(String pocEffectKey) {
        this.pocEffectKey = pocEffectKey;
    }

    public List<String> getSupportedEffectKeys() {
        if (supportedEffectKeys == null || supportedEffectKeys.isEmpty()) {
            return List.of(pocEffectKey);
        }
        return supportedEffectKeys;
    }

    public void setSupportedEffectKeys(List<String> supportedEffectKeys) {
        this.supportedEffectKeys = supportedEffectKeys;
    }

    public String getPocScriptPath() {
        return pocScriptPath;
    }

    public void setPocScriptPath(String pocScriptPath) {
        this.pocScriptPath = pocScriptPath;
    }

    public String getRendererBinary() {
        return rendererBinary;
    }

    public void setRendererBinary(String rendererBinary) {
        this.rendererBinary = rendererBinary;
    }

    public boolean isFallbackToFfmpeg() {
        return fallbackToFfmpeg;
    }

    public void setFallbackToFfmpeg(boolean fallbackToFfmpeg) {
        this.fallbackToFfmpeg = fallbackToFfmpeg;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String getReaderNodeName() {
        return readerNodeName;
    }

    public void setReaderNodeName(String readerNodeName) {
        this.readerNodeName = readerNodeName;
    }

    public String getWriterNodeName() {
        return writerNodeName;
    }

    public void setWriterNodeName(String writerNodeName) {
        this.writerNodeName = writerNodeName;
    }

    public String getBatchScriptTemplate() {
        return batchScriptTemplate;
    }

    public void setBatchScriptTemplate(String batchScriptTemplate) {
        this.batchScriptTemplate = batchScriptTemplate;
    }

    public boolean isAutoSelectProfile() {
        return autoSelectProfile;
    }

    public void setAutoSelectProfile(boolean autoSelectProfile) {
        this.autoSelectProfile = autoSelectProfile;
    }
}
