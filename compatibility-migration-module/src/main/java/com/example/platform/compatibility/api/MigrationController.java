package com.example.platform.compatibility.api;

import com.example.platform.compatibility.domain.*;
import com.example.platform.compatibility.service.MigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/internal/migrations")
public class MigrationController {

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/dry-run")
    public ResponseEntity<MigrationResult> dryRun(@RequestBody DryRunRequest request) {
        VersionedPayload payload = new VersionedPayload(
                request.schemaFamily(), request.schemaVersion(),
                request.payload(), request.metadata()
        );
        MigrationAuditContext ctx = new MigrationAuditContext(
                UUID.randomUUID().toString(), request.tenantId(), "system",
                request.sourceObjectRef(), true, "API"
        );
        MigrationResult result = migrationService.dryRun(payload, request.targetVersion(), ctx);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run")
    public ResponseEntity<MigrationResult> run(@RequestBody RunRequest request) {
        VersionedPayload payload = new VersionedPayload(
                request.schemaFamily(), request.schemaVersion(),
                request.payload(), request.metadata()
        );
        MigrationAuditContext ctx = new MigrationAuditContext(
                UUID.randomUUID().toString(), request.tenantId(), request.userId(),
                request.sourceObjectRef(), false, "API"
        );
        MigrationResult result = migrationService.migrate(payload, request.targetVersion(), ctx);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listMigrations(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(Map.of(
                "migrations", List.of(),
                "tenantId", tenantId
        ));
    }

    public record DryRunRequest(
            SchemaFamily schemaFamily,
            SchemaVersion schemaVersion,
            SchemaVersion targetVersion,
            Map<String, Object> payload,
            Map<String, String> metadata,
            String tenantId,
            String sourceObjectRef
    ) {}

    public record RunRequest(
            SchemaFamily schemaFamily,
            SchemaVersion schemaVersion,
            SchemaVersion targetVersion,
            Map<String, Object> payload,
            Map<String, String> metadata,
            String tenantId,
            String userId,
            String sourceObjectRef
    ) {}
}
