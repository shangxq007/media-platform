package com.example.platform.render.infrastructure.semantic;

/**
 * User intent behind a render job request.
 */
public record UserIntent(
        String intentType,
        String description,
        String label
) {
    /**
     * Create a render video intent.
     */
    public static UserIntent renderVideo() {
        return new UserIntent("RENDER_VIDEO", "User wants to render a video", "Render Video");
    }

    /**
     * Create a preview intent.
     */
    public static UserIntent preview() {
        return new UserIntent("PREVIEW", "User wants to preview a video", "Preview Video");
    }

    /**
     * Create a template render intent.
     */
    public static UserIntent templateRender() {
        return new UserIntent("TEMPLATE_RENDER", "User wants to render from a template", "Template Render");
    }

    /**
     * Create a batch render intent.
     */
    public static UserIntent batchRender() {
        return new UserIntent("BATCH_RENDER", "User wants to render multiple videos", "Batch Render");
    }

    /**
     * Create an export intent.
     */
    public static UserIntent exportProject() {
        return new UserIntent("EXPORT_PROJECT", "User wants to export a project", "Export Project");
    }

    /**
     * Infer intent from context.
     */
    public static UserIntent inferFromContext(String profile, boolean isBatch) {
        if (isBatch) return batchRender();
        if (profile != null && profile.contains("template")) return templateRender();
        if (profile != null && profile.contains("preview")) return preview();
        return renderVideo();
    }
}
