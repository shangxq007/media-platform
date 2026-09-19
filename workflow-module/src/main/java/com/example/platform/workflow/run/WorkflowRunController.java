package com.example.platform.workflow.run;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/workflow-executions")
public class WorkflowRunController {
    private final WorkflowRunService service;

    public WorkflowRunController(WorkflowRunService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRunService.View start(
            @PathVariable String tenantId, @RequestBody String request) {
        return service.start(tenantId, RunJson.read(request, WorkflowRunService.Start.class));
    }

    @GetMapping("/{id}")
    public WorkflowRunService.View get(@PathVariable String tenantId, @PathVariable String id) {
        return service.get(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    public WorkflowRunService.View cancel(
            @PathVariable String tenantId,
            @PathVariable String id,
            @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank() && !RunJson.read(body, java.util.Map.class).isEmpty())
            throw new IllegalArgumentException(
                    "Cancellation has no caller-supplied actor or scope fields");
        return service.cancel(tenantId, id);
    }

    public record Release(String stepId, String releaseId, Boolean approved) {
        public Release {
            if (stepId == null
                    || stepId.isBlank()
                    || releaseId == null
                    || releaseId.isBlank()
                    || approved == null)
                throw new IllegalArgumentException(
                        "Explicit wait, release identity and decision required");
        }
    }

    @PostMapping("/{id}/release")
    public WorkflowRunService.View release(
            @PathVariable String tenantId, @PathVariable String id, @RequestBody String body) {
        Release request = RunJson.read(body, Release.class);
        return service.release(
                tenantId, id, request.stepId(), request.releaseId(), request.approved());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage()));
    }
}
