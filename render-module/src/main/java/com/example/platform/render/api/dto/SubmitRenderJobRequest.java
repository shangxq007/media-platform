package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request to submit a render job for execution.
 *
 * <p>This DTO is part of the render module's public API surface and can be
 * used by other modules to submit render jobs through the
 * {@link com.example.platform.render.api.port.RenderOrchestratorPort}.</p>
 *
 * @param tenantId  the tenant identifier (must not be blank)
 * @param projectId the project identifier (must not be blank)
 * @param prompt    the AI prompt/script, or inline timeline JSON (optional when snapshot exists)
 * @param profile   the render profile (e.g., "default_1080p", "4k"); defaults to "default_1080p" if blank
 * @param baseJobId optional prior completed job for incremental DAG reuse
 * @param targetSegmentIds optional subset of {@code seg_*} tasks to execute (others reuse base job)
 * @param editSessionId optional multi-turn edit session id (stored in timeline metadata)
 * @param aiEditIntent short intent label for audit (e.g. replace_bgm)
 * @param aiEditInstruction natural-language edit; requires {@code baseJobId}, runs before render
 */
@Schema(description = "提交渲染/增量渲染作业")
public record SubmitRenderJobRequest(
        @NotBlank @Schema(description = "租户 ID") String tenantId,
        @NotBlank @Schema(description = "项目 ID") String projectId,
        @Schema(description = "AI 脚本或 inline Internal Timeline 1.0 JSON") String prompt,
        @Schema(example = "default_1080p") String profile,
        @Schema(description = "时间线快照 ID") String timelineSnapshotId,
        @Schema(description = "增量基准作业 ID") String baseJobId,
        @Schema(description = "局部段渲染：仅执行列出的 seg_* 任务") List<String> targetSegmentIds,
        @Schema(description = "AI 多轮编辑会话 ID") String editSessionId,
        @Schema(description = "AI 编辑意图标签") String aiEditIntent,
        @Schema(description = "AI 自然语言改时间线（需 baseJobId）") String aiEditInstruction) {

    public SubmitRenderJobRequest(
            String tenantId,
            String projectId,
            String prompt,
            String profile,
            String timelineSnapshotId) {
        this(tenantId, projectId, prompt, profile, timelineSnapshotId, null, null, null, null, null);
    }

    public SubmitRenderJobRequest(
            String tenantId,
            String projectId,
            String prompt,
            String profile,
            String timelineSnapshotId,
            String baseJobId) {
        this(tenantId, projectId, prompt, profile, timelineSnapshotId, baseJobId, null, null, null, null);
    }

    public static SubmitRenderJobRequest withPrompt(String tenantId, String projectId, String prompt, String profile) {
        return new SubmitRenderJobRequest(tenantId, projectId, prompt, profile, null, null, null, null, null, null);
    }

    public static SubmitRenderJobRequest withSnapshot(String tenantId, String projectId,
            String timelineSnapshotId, String profile) {
        return new SubmitRenderJobRequest(tenantId, projectId, null, profile, timelineSnapshotId, null, null, null, null, null);
    }

    public static SubmitRenderJobRequest incremental(String tenantId, String projectId,
            String timelineSnapshotId, String profile, String baseJobId) {
        return new SubmitRenderJobRequest(tenantId, projectId, null, profile, timelineSnapshotId, baseJobId, null, null, null, null);
    }

    public static SubmitRenderJobRequest segmentRender(String tenantId, String projectId,
            String timelineSnapshotId, String profile, String baseJobId, List<String> targetSegmentIds) {
        return new SubmitRenderJobRequest(
                tenantId, projectId, null, profile, timelineSnapshotId, baseJobId, targetSegmentIds, null, null, null);
    }

    /**
     * Returns the profile value or a default if blank.
     *
     * @return the render profile, never null or blank
     */
    public String profileOrDefault() {
        return (profile == null || profile.isBlank()) ? "default_1080p" : profile;
    }
}
