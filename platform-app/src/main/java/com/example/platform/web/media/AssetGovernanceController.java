package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.render.app.timeline.TimelineAssetGcService;
import com.example.platform.security.AdminAuditHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset-governance")
@Tag(name = "Asset Governance", description = "Cross-layer GC and global integrity scans")
public class AssetGovernanceController {

    private final GlobalAssetIntegrityService globalIntegrityService;
    private final TimelineAssetGcService timelineAssetGcService;
    private final ArtifactGcService artifactGcService;
    private final AdminAuditHelper auditHelper;

    public AssetGovernanceController(
            GlobalAssetIntegrityService globalIntegrityService,
            TimelineAssetGcService timelineAssetGcService,
            ArtifactGcService artifactGcService,
            AdminAuditHelper auditHelper) {
        this.globalIntegrityService = globalIntegrityService;
        this.timelineAssetGcService = timelineAssetGcService;
        this.artifactGcService = artifactGcService;
        this.auditHelper = auditHelper;
    }

    @PostMapping("/integrity/scan-global")
    @Operation(summary = "跨项目全局完整性扫描", description = "聚合 AST 规则计数并更新 asset.integrity.* 指标")
    public GlobalAssetIntegrityService.GlobalScanReport scanGlobal(
            @RequestParam(defaultValue = "true") boolean recordProblematicData) {
        return globalIntegrityService.scanAll(recordProblematicData);
    }


    private static boolean isAdmin(jakarta.servlet.http.HttpServletRequest request) {
        if (request.isUserInRole("ADMIN")) return true;
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if ("ADMIN".equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }

    public record CombinedGcResult(
            ArtifactGcService.GcResult artifactGc,
            TimelineAssetGcService.GcRunResult timelineGc) {}
}
