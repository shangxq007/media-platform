package com.example.platform.web.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media/assets/integrity")
@Tag(name = "Asset Integrity", description = "Timeline + catalog integrity scan (problematic data AST-*)")
public class AssetIntegrityScanController {

    private final AssetIntegrityScanService scanService;
    private final GlobalAssetIntegrityService globalIntegrityService;

    public AssetIntegrityScanController(
            AssetIntegrityScanService scanService,
            GlobalAssetIntegrityService globalIntegrityService) {
        this.scanService = scanService;
        this.globalIntegrityService = globalIntegrityService;
    }

    @PostMapping("/scan")
    @Operation(summary = "扫描项目资产完整性", description = "检测悬空 assetId、孤儿 blob 等并写入问题数据审计")
    public AssetIntegrityScanService.ScanReport scan(@RequestParam @NotBlank String projectId) {
        return scanService.scanProject(projectId);
    }

    @PostMapping("/scan-global")
    @Operation(summary = "跨项目全局扫描", description = "更新 Prometheus 指标 asset.integrity.*")
    public GlobalAssetIntegrityService.GlobalScanReport scanGlobal(
            @RequestParam(defaultValue = "false") boolean recordProblematicData) {
        return globalIntegrityService.scanAll(recordProblematicData);
    }
}
