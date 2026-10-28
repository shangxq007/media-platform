package com.example.platform.coverimage;

import com.example.platform.config.mediaexecution.ArtifactCapabilityApplicationService;
import com.example.platform.contract.media.CoverImageContracts;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort.CapabilityHandle;
import com.example.platform.shared.web.TenantContext;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Thin media intent adapter; no cover task, planning, execution state or commit authority. */
@RestController
@RequestMapping("/api/projects/{projectId}/cover-images")
public class CoverImageController {
    private final ArtifactCapabilityApplicationService executions;
    public CoverImageController(ArtifactCapabilityApplicationService executions) { this.executions=executions; }
    public record CreateRequest(String subjectArtifactId,double timestampSeconds,String imageFormat,
            Integer width,Integer quality,String idempotencyKey) {}
    public record Control(long generation) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CapabilityHandle submit(@PathVariable String projectId,@RequestBody CreateRequest body) {
        var request=new CoverImageContracts.Request(tenant(),projectId,body.subjectArtifactId(),body.timestampSeconds(),
                body.imageFormat(),body.width(),body.quality(),body.idempotencyKey());
        return executions.submit(request.tenantId(),projectId,CoverImageContracts.CAPABILITY,request.subjectArtifactId(),
                request.capabilityParameters(),request.idempotencyKey()).handle();
    }
    @GetMapping("/{executionId}")
    public CapabilityHandle status(@PathVariable String projectId,@PathVariable String executionId) {
        return executions.find(tenant(),projectId,executionId,CoverImageContracts.CAPABILITY)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)).handle();
    }
    @PostMapping("/{executionId}/cancel")
    public CapabilityHandle cancel(@PathVariable String projectId,@PathVariable String executionId,@RequestBody Control control) {
        return executions.cancel(tenant(),projectId,executionId,control.generation(),CoverImageContracts.CAPABILITY).handle();
    }
    @PostMapping("/{executionId}/retry")
    public CapabilityHandle retry(@PathVariable String projectId,@PathVariable String executionId,@RequestBody Control control) {
        return executions.retry(tenant(),projectId,executionId,control.generation(),CoverImageContracts.CAPABILITY).handle();
    }
    private static String tenant() {
        var tenant=TenantContext.get();
        if(tenant==null||tenant.isBlank())throw new IllegalStateException("authenticated tenant required");
        return tenant;
    }
}
