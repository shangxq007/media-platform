package com.example.platform.render.infrastructure;

/**
 * Unified capability constants for all render providers.
 * These represent the full set of capabilities that any provider can declare.
 */
public final class Capabilities {
    private Capabilities() {}

    public static final String TRIM = "trim";
    public static final String TRANSCODE = "transcode";
    public static final String MUX = "mux";
    public static final String DEMUX = "demux";
    public static final String EXTRACT_AUDIO = "extract_audio";
    public static final String THUMBNAIL = "thumbnail";
    public static final String CAPTION_BURN_IN = "caption_burn_in";
    public static final String CAPTION_EFFECTS = "caption_effects";
    public static final String SUBTITLE_OVERLAY = "subtitle_overlay";
    public static final String ASS_SSA_RENDER = "ass_ssa_render";
    public static final String TEMPLATE_RENDER = "template_render";
    public static final String PREVIEW = "preview";
    public static final String TIMELINE_RENDER = "timeline_render";
    public static final String MULTI_TRACK = "multi_track";
    public static final String TRANSITION = "transition";
    public static final String AUDIO_MIX = "audio_mix";
    public static final String RENDER_3D = "3d_render";
    public static final String VFX_COMPOSITE = "vfx_composite";
    public static final String PREPROCESS = "preprocess";
    public static final String DENOISE = "denoise";
    public static final String DEINTERLACE = "deinterlace";
    public static final String FPS_CONVERT = "fps_convert";
    public static final String VIDEO_ENHANCE = "video_enhance";
    public static final String MEDIA_PIPELINE = "media_pipeline";
    public static final String AI_MEDIA_PIPELINE = "ai_media_pipeline";
    public static final String GRAPH_BASED_PROCESSING = "graph_based_processing";
    public static final String PACKAGE_HLS = "package_hls";
    public static final String PACKAGE_DASH = "package_dash";
    public static final String PACKAGE_CMAF = "package_cmaf";
    public static final String CLOUD_RENDER = "cloud_render";
    public static final String EXTERNAL_RENDER = "external_render";
    public static final String OUTPUT_NORMALIZE = "output_normalize";
}
