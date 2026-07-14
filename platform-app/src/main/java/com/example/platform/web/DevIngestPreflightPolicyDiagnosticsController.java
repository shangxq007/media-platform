package com.example.platform.web;

import com.example.platform.ingest.preflight.policy.diagnostics.IngestPreflightPolicyConfigDiagnostics;
import com.example.platform.ingest.preflight.policy.diagnostics.IngestPreflightPolicyDecisionSemanticsDiagnostics;
import com.example.platform.ingest.preflight.policy.diagnostics.IngestPreflightPolicyDiagnosticsResponse;
import com.example.platform.ingest.preflight.policy.diagnostics.IngestPreflightPolicyDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal read-only diagnostics for ingest preflight policy.
 * GET only. No upload execution. No policy evaluation on real media.
 */
@RestController
@org.springframework.context.annotation.Profile("dev")
@RequestMapping("/dev/ingest/preflight-policy")
public class DevIngestPreflightPolicyDiagnosticsController {

    private final IngestPreflightPolicyDiagnosticsService service;

    public DevIngestPreflightPolicyDiagnosticsController(IngestPreflightPolicyDiagnosticsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<IngestPreflightPolicyDiagnosticsResponse> getDiagnostics() {
        return ResponseEntity.ok(service.getDiagnostics());
    }

    @GetMapping("/config")
    public ResponseEntity<IngestPreflightPolicyConfigDiagnostics> getConfigDiagnostics() {
        return ResponseEntity.ok(service.getConfigDiagnostics());
    }

    @GetMapping("/decision-semantics")
    public ResponseEntity<IngestPreflightPolicyDecisionSemanticsDiagnostics> getDecisionSemantics() {
        return ResponseEntity.ok(service.getDecisionSemantics());
    }
}
