package com.example.platform.workflow.execution.api;

import com.example.platform.shared.authorization.FailClosedAuthorization;
import com.example.platform.workflow.execution.api.dto.WorkflowExecutionDto;
import com.example.platform.workflow.execution.app.WorkflowExecutionService;
import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UWEV1-FV1 execution API — new resource, does NOT touch the 9 W2 definition
 * routes. Authorization via APPD canonical actions (UWE-ADR-023).
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/workflow-executions")
public class WorkflowExecutionController {

    private final WorkflowExecutionService service;

    public WorkflowExecutionController(WorkflowExecutionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WorkflowExecutionDto> start(
            @PathVariable String tenantId,
            @RequestBody StartWorkflowExecutionRequest request) {
        throw FailClosedAuthorization.unavailable("workflow execution start");
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<WorkflowExecutionDto> get(
            @PathVariable String tenantId,
            @PathVariable String executionId) {
        return service.findById(new WorkflowExecutionId(executionId, tenantId))
                .map(e -> ResponseEntity.ok(WorkflowExecutionDto.from(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{executionId}/cancel")
    public ResponseEntity<WorkflowExecutionDto> cancel(
            @PathVariable String tenantId,
            @PathVariable String executionId,
            @RequestBody(required = false) CancelRequest request) {
        throw FailClosedAuthorization.unavailable("workflow execution cancellation");
    }

    @PostMapping("/{executionId}/approval")
    public ResponseEntity<WorkflowExecutionDto> approve(
            @PathVariable String tenantId,
            @PathVariable String executionId,
            @RequestBody ApprovalRequest request) {
        throw FailClosedAuthorization.unavailable("workflow execution approval");
    }

    // ── request DTOs (bounded, typed; no raw payloads) ────────────────────
    public record StartWorkflowExecutionRequest(
            String actorId,
            String actorType,
            String definitionId,
            Integer definitionVersion,
            com.example.platform.workflow.execution.domain.WorkflowExecutionTrigger trigger,
            String inputRefsJson,
            String idempotencyKey) {
    }

    public record CancelRequest(String actorId, String actorType, String reason) {
    }

    public record ApprovalRequest(boolean approved, String approverActorId, String comment) {
    }
}
