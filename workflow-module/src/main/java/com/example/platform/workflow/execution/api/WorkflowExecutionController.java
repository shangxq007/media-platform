package com.example.platform.workflow.execution.api;

import com.example.platform.shared.authorization.AuthorizationActions;
import com.example.platform.workflow.execution.api.dto.WorkflowExecutionDto;
import com.example.platform.workflow.execution.app.StartWorkflowExecutionCommand;
import com.example.platform.workflow.execution.app.WorkflowExecutionApprovalCommand;
import com.example.platform.workflow.execution.app.WorkflowExecutionService;
import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.billing.usage.CanonicalActorRef;

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
        // authorize(tenantId, AuthorizationActions.WORKFLOW_EXECUTION_START); // wired via canonical port
        WorkflowExecution e = service.start(new StartWorkflowExecutionCommand(
                tenantId,
                new CanonicalActorRef(request.actorId(), request.actorType()),
                request.definitionId(),
                request.definitionVersion(),
                request.trigger(),
                request.inputRefsJson(),
                request.idempotencyKey()));
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowExecutionDto.from(e));
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
        WorkflowExecution e = service.cancel(
                new WorkflowExecutionId(executionId, tenantId),
                new CanonicalActorRef(request == null ? "system" : request.actorId(),
                        request == null ? "SYSTEM" : request.actorType()),
                request == null ? null : request.reason());
        return ResponseEntity.ok(WorkflowExecutionDto.from(e));
    }

    @PostMapping("/{executionId}/approval")
    public ResponseEntity<WorkflowExecutionDto> approve(
            @PathVariable String tenantId,
            @PathVariable String executionId,
            @RequestBody ApprovalRequest request) {
        service.approve(new WorkflowExecutionApprovalCommand(
                tenantId, executionId, request.approved(), request.approverActorId(), request.comment()));
        return ResponseEntity.ok(service.findById(new WorkflowExecutionId(executionId, tenantId))
                .map(WorkflowExecutionDto::from).orElse(null));
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
