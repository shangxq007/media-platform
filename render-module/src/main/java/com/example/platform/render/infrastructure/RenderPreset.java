package com.example.platform.render.infrastructure;

/**
 * Standard render presets defining codec, resolution, bitrate, and quality settings.
 */
public enum RenderPreset {

    DEFAULT("default", "H.264/AAC 1080p", "libx264", "aac", "1920x1080", 30, 8000, 22050, 2, 44100, 128, false),
    H265("h265", "H.265/AAC 1080p", "libx265", "aac", "1920x1080", 30, 6000, 22050, 2, 44100, 128, false),
    VP9("vp9", "VP9/Opus 1080p", "libvpx-vp9", "libopus", "1920x1080", 30, 6000, 22050, 2, 48000, 128, false),
    PREVIEW_720P("preview_720p", "H.264/AAC 720p Preview", "libx264", "aac", "1280x720", 30, 2500, 22050, 2, 44100, 96, false),
    HQ_1080P("hq_1080p", "H.264/AAC 1080p High Quality", "libx264", "aac", "1920x1080", 30, 12000, 22050, 2, 44100, 192, false),
    GPU_H264("gpu_h264", "H.264 NVENC GPU 1080p", "h264_nvenc", "aac", "1920x1080", 30, 8000, 22050, 2, 44100, 128, true),
    GPU_H265("gpu_h265", "H.265 NVENC GPU 1080p", "hevc_nvenc", "aac", "1920x1080", 30, 6000, 22050, 2, 44100, 128, true),
    GPU_VP9("gpu_vp9", "VP9 VAAPI GPU 1080p", "vp9_vaapi", "aac", "1920x1080", 30, 6000, 22050, 2, 44100, 128, true);

    private final String key;
    private final String label;
    private final String videoCodec;
    private final String audioCodec;
    private final String resolution;
    private final int frameRate;
    private final int videoBitrateKbps;
    private final int pixelFormat;
    private final int audioChannels;
    private final int sampleRate;
    private final int audioBitrateKbps;
    private final boolean requiresGpu;

    RenderPreset(String key, String label, String videoCodec, String audioCodec,
                 String resolution, int frameRate, int videoBitrateKbps,
                 int pixelFormat, int audioChannels, int sampleRate, int audioBitrateKbps,
                 boolean requiresGpu) {
        this.key = key;
        this.label = label;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.resolution = resolution;
        this.frameRate = frameRate;
        this.videoBitrateKbps = videoBitrateKbps;
        this.pixelFormat = pixelFormat;
        this.audioChannels = audioChannels;
        this.sampleRate = sampleRate;
        this.audioBitrateKbps = audioBitrateKbps;
        this.requiresGpu = requiresGpu;
    }

    public String key() { return key; }
    public String label() { return label; }
    public String videoCodec() { return videoCodec; }
    public String audioCodec() { return audioCodec; }
    public String resolution() { return resolution; }
    public int frameRate() { return frameRate; }
    public int videoBitrateKbps() { return videoBitrateKbps; }
    public int pixelFormat() { return pixelFormat; }
    public int audioChannels() { return audioChannels; }
    public int sampleRate() { return sampleRate; }
    public int audioBitrateKbps() { return audioBitrateKbps; }
    public boolean requiresGpu() { return requiresGpu; }

    public int width() {
        return Integer.parseInt(resolution.split("x")[0]);
    }

    public int height() {
        return Integer.parseInt(resolution.split("x")[1]);
    }

    /**
     * Map a profile string to the best matching preset.
     * Handles both new preset names (h265, vp9, preview_720p, hq_1080p)
     * and legacy profile names (default_1080p, social_720p, etc.).
     */
    public static RenderPreset fromProfile(String profile) {
        if (profile == null || profile.isBlank()) return DEFAULT;
        String lower = profile.toLowerCase();

        // New preset names - direct match
        switch (lower) {
            case "h265": return H265;
            case "vp9": return VP9;
            case "preview_720p": case "preview": return PREVIEW_720P;
            case "hq_1080p": case "hq": return HQ_1080P;
            case "gpu_h264": case "gpu": return GPU_H264;
            case "gpu_h265": case "gpu_hevc": return GPU_H265;
            case "gpu_vp9": return GPU_VP9;
            case "natron_poc_1080p": return HQ_1080P;
            case "natron_poc_720p": return PREVIEW_720P;
            case "shotstack_social_1080p": return HQ_1080P;
            case "shotstack_social_720p": return PREVIEW_720P;
            default: break;
        }

        // Legacy profile names - match by resolution hint
        if (lower.contains("720") || lower.contains("480") || lower.contains("mobile")) {
            return PREVIEW_720P;
        }
        if (lower.contains("4k") || lower.contains("2160")) {
            return HQ_1080P;
        }

        return DEFAULT;
    }
}
