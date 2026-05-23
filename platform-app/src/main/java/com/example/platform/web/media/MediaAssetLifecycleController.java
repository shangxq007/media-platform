package com.example.platform.web.media;

import com.example.platform.render.app.timeline.TimelineAssetGcService;
import com.example.platform.render.app.timeline.TimelineAssetLifecycleService;
import com.example.platform.shared.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media/assets")
@Tag(name = "Media Assets", description = "Timeline assetRegistry lifecycle (tombstone, delete-check)")
public class MediaAssetLifecycleController {

    private final TimelineAssetLifecycleService lifecycleService;
    private final TimelineAssetGcService timelineAssetGcService;

    public MediaAssetLifecycleController(
            TimelineAssetLifecycleService lifecycleService,
            TimelineAssetGcService timelineAssetGcService) {
        this.lifecycleService = lifecycleService;
        this.timelineAssetGcService = timelineAssetGcService;
    }

    @GetMapping("/{assetId}/delete-check")
    @Operation(summary = "删除前引用检查", description = "扫描项目下时间线快照，列出仍引用该 assetId 的 clip/layer")
    public TimelineAssetLifecycleService.DeleteCheckResult deleteCheck(
            @PathVariable String assetId,
            @RequestParam @NotBlank String projectId) {
        return lifecycleService.deleteCheck(projectId, assetId);
    }

    @PostMapping("/{assetId}/tombstone")
    @Operation(summary = "标记资产 tombstone", description = "在无活跃引用时更新 assetRegistry 状态并写入新快照")
    public TimelineAssetLifecycleService.TombstoneResult tombstone(
            @PathVariable String assetId,
            @RequestParam @NotBlank String projectId,
            @RequestParam @NotBlank String snapshotId) {
        return lifecycleService.tombstone(projectId, snapshotId, assetId, TenantContext.get());
    }

    @PostMapping("/gc/run")
    @Operation(summary = "时间线 assetRegistry GC", description = "清理超过保留期且无引用的 TOMBSTONED 条目")
    public TimelineAssetGcService.GcProjectResult runProjectGc(@RequestParam @NotBlank String projectId) {
        return timelineAssetGcService.runProjectGc(projectId, TenantContext.get());
    }
}
