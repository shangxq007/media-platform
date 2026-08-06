@org.springframework.modulith.NamedInterface("plugin")
package com.example.platform.render.infrastructure.plugin;

/**
 * Render-side plugin self-description adapter package (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Owns the FFmpeg/render self-description contributor
 * ({@code FfmpegRenderToolSelfDescription}), which registers the existing
 * render tool capability into the extension-module PluginRegistry (descriptor
 * authority). Dependency direction: render-module -&gt; extension-module
 * (existing direction; no new cycle). No execution path is changed.</p>
 */
