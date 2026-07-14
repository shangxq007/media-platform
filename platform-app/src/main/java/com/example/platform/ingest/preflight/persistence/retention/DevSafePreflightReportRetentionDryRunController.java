package com.example.platform.ingest.preflight.persistence.retention;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("dev")
@RequestMapping("/dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports/retention")
public class DevSafePreflightReportRetentionDryRunController {

    private final SafePreflightReportRetentionDryRunService service;

    public DevSafePreflightReportRetentionDryRunController(SafePreflightReportRetentionDryRunService service) {
        this.service = service;
    }

    @GetMapping("/dry-run")
    public ResponseEntity<SafePreflightReportRetentionDryRunResponse> dryRun(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestParam(defaultValue = "100") int batchLimit,
            @RequestParam(defaultValue = "PHYSICAL_DELETE_CANDIDATE") SafePreflightReportRetentionDryRunStrategy strategy) {

        var response = service.executeDryRun(tenantId, projectId, batchLimit, strategy);
        return ResponseEntity.ok(response);
    }
}
