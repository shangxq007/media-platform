package com.example.platform.render.app.output;

import java.util.List;
import java.util.Map;

/**
 * Internal value object carrying render provenance metadata from timeline/render
 * context into final Product metadataJson.
 *
 * <p>Used by {@link RenderOutputRegistrationService} to enrich final render
 * Products with timeline, render, and storage provenance. Does NOT change
 * Product model semantics — provenance is stored in the existing metadataJson field.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Does not expose internal provider/backend/environment selection</li>
 *   <li>Does not persist signed URLs or absolute filesystem paths</li>
 *   <li>Does not change ProductRuntime or StorageRuntime semantic contracts</li>
 * </ul>
 */
public record RenderProductProvenance(
        String tenantId,
        String projectId,
        String timelineId,
        String timelineRevisionId,
        String snapshotId,
        String renderJobId,
        String executionJobId,
        String outputProfile,
        String outputFormat,
        Double durationSeconds,
        Integer fps,
        Integer width,
        Integer height,
        Boolean hasSubtitles,
        String subtitleFormat,
        String baselineRenderer,
        String renderMode,
        List<String> inputProductIds,
        List<String> sourceAssetIds
) {
    /**
     * Builder for RenderProductProvenance with sensible defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String projectId;
        private String timelineId;
        private String timelineRevisionId;
        private String snapshotId;
        private String renderJobId;
        private String executionJobId;
        private String outputProfile;
        private String outputFormat;
        private Double durationSeconds;
        private Integer fps;
        private Integer width;
        private Integer height;
        private Boolean hasSubtitles;
        private String subtitleFormat;
        private String baselineRenderer;
        private String renderMode;
        private List<String> inputProductIds;
        private List<String> sourceAssetIds;

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder projectId(String projectId) { this.projectId = projectId; return this; }
        public Builder timelineId(String timelineId) { this.timelineId = timelineId; return this; }
        public Builder timelineRevisionId(String timelineRevisionId) { this.timelineRevisionId = timelineRevisionId; return this; }
        public Builder snapshotId(String snapshotId) { this.snapshotId = snapshotId; return this; }
        public Builder renderJobId(String renderJobId) { this.renderJobId = renderJobId; return this; }
        public Builder executionJobId(String executionJobId) { this.executionJobId = executionJobId; return this; }
        public Builder outputProfile(String outputProfile) { this.outputProfile = outputProfile; return this; }
        public Builder outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder fps(Integer fps) { this.fps = fps; return this; }
        public Builder width(Integer width) { this.width = width; return this; }
        public Builder height(Integer height) { this.height = height; return this; }
        public Builder hasSubtitles(Boolean hasSubtitles) { this.hasSubtitles = hasSubtitles; return this; }
        public Builder subtitleFormat(String subtitleFormat) { this.subtitleFormat = subtitleFormat; return this; }
        public Builder baselineRenderer(String baselineRenderer) { this.baselineRenderer = baselineRenderer; return this; }
        public Builder renderMode(String renderMode) { this.renderMode = renderMode; return this; }
        public Builder inputProductIds(List<String> inputProductIds) { this.inputProductIds = inputProductIds; return this; }
        public Builder sourceAssetIds(List<String> sourceAssetIds) { this.sourceAssetIds = sourceAssetIds; return this; }

        public RenderProductProvenance build() {
            return new RenderProductProvenance(
                    tenantId, projectId, timelineId, timelineRevisionId, snapshotId,
                    renderJobId, executionJobId, outputProfile, outputFormat,
                    durationSeconds, fps, width, height, hasSubtitles, subtitleFormat,
                    baselineRenderer, renderMode, inputProductIds, sourceAssetIds);
        }
    }
}
