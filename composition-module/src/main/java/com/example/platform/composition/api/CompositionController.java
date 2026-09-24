package com.example.platform.composition.api;

import com.example.platform.composition.app.CompositionService;
import com.example.platform.composition.domain.CompositionModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/composition")
public class CompositionController {
    private final CompositionService service;
    private final com.example.platform.composition.app.CompositionAccess access;
    private final com.example.platform.composition.app.CompositionAdmissionService admission;
    public CompositionController(CompositionService service,com.example.platform.composition.app.CompositionAccess access, com.example.platform.composition.app.CompositionAdmissionService admission){this.service=service;this.access=access;this.admission=admission;}
    @Operation(operationId = "admitComposition", summary = "Admit a published Composition for durable execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AdmissionDecision persisted"),
            @ApiResponse(responseCode = "403", description = "Authenticated scope or workspace membership rejected"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflicts with an existing decision"),
            @ApiResponse(responseCode = "422", description = "Published revision, resource, entitlement or quota validation failed")
    })
    @PostMapping("/admissions") public AdmissionResponse admit(@RequestBody com.example.platform.composition.app.CompositionAdmissionRequest request){
        var decision=admission.admit(request);
        var plan=decision.plan();
        return new AdmissionResponse(decision.executionId(), decision.ownershipGeneration(), decision.state(), decision.newlyAdmitted(),
                plan.publishedRevision().subjectId(), plan.publishedRevision().version(), plan.publishedRevision().revision(), plan.planFingerprint().value());
    }
    public record AdmissionResponse(String executionId, long ownershipGeneration, String state, boolean newlyAdmitted,
                                    String compositionId, String version, long revision, String planFingerprint) {}
    @GetMapping("/scope") public com.example.platform.composition.app.CompositionAccess.Scope scope(@RequestParam(required=false) String workspaceId){return access.resolve(workspaceId);}
    @GetMapping("/capabilities") public List<CapabilityAvailability> catalog(@RequestParam(required=false) String workspaceId){return service.catalog(workspaceId);}
    @PostMapping("/workflows/drafts") public TemplateWorkflow saveWorkflow(@RequestBody TemplateWorkflow v,HttpServletRequest r){return service.saveWorkflow(v,tenant(r));}
    @GetMapping("/workflows/drafts/{id}") public TemplateWorkflow workflow(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.workflow(tenant(r),workspaceId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/workflows/versions/{id}/{version}") public TemplateWorkflow workflowVersion(@PathVariable String id,@PathVariable String version,@RequestParam String workspaceId,HttpServletRequest r){return service.workflowVersion(tenant(r),workspaceId,id,version).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/workflows/versions/{id}/compare") public Map<String,Object> compareWorkflow(@PathVariable String id,@RequestParam String workspaceId,@RequestParam String left,@RequestParam String right,HttpServletRequest r){return service.compareWorkflowVersions(tenant(r),workspaceId,id,left,right);}
    @PostMapping("/workflows/validate") public ValidationResult validateWorkflow(@RequestBody TemplateWorkflow v,@RequestParam(defaultValue="") Set<String> assets,HttpServletRequest r){return service.validateWorkflow(v,tenant(r),assets);}
    @PutMapping("/workflows/drafts/{id}") public TemplateWorkflow updateWorkflow(@PathVariable String id,@RequestBody TemplateWorkflow v,HttpServletRequest r){return saveWorkflow(v,r);}
    @PostMapping("/workflows/publish-readiness") public ValidationResult workflowReadiness(@RequestBody TemplateWorkflow v,HttpServletRequest r){return service.validateWorkflow(v,tenant(r),Set.of());}
    @PostMapping("/workflows/drafts/{id}/publish") public TemplateWorkflow publishWorkflow(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.publishWorkflow(tenant(r),workspaceId,id);}
    @PostMapping("/applications/drafts") public Application saveApplication(@RequestBody Application v,HttpServletRequest r){return service.saveApplication(v,tenant(r));}
    @GetMapping("/applications/drafts/{id}") public Application application(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.application(tenant(r),workspaceId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/applications/versions/{id}/{version}") public Application applicationVersion(@PathVariable String id,@PathVariable String version,@RequestParam String workspaceId,HttpServletRequest r){return service.applicationVersion(tenant(r),workspaceId,id,version).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/applications/versions/{id}/compare") public Map<String,Object> compareApplication(@PathVariable String id,@RequestParam String workspaceId,@RequestParam String left,@RequestParam String right,HttpServletRequest r){return service.compareApplicationVersions(tenant(r),workspaceId,id,left,right);}
    @PostMapping("/applications/validate") public ValidationResult validateApplication(@RequestBody Application v,@RequestParam(defaultValue="") Set<String> assets,@RequestParam(defaultValue="") Set<String> entitlements,HttpServletRequest r){return service.validateApplication(v,tenant(r),assets,entitlements);}
    @PutMapping("/applications/drafts/{id}") public Application updateApplication(@PathVariable String id,@RequestBody Application v,HttpServletRequest r){return saveApplication(v,r);}
    @PostMapping("/applications/publish-readiness") public ValidationResult applicationReadiness(@RequestBody Application v,HttpServletRequest r){return service.validateApplication(v,tenant(r),Set.of(),Set.of());}
    @PostMapping("/applications/drafts/{id}/publish") public Application publishApplication(@PathVariable String id,@RequestParam String workspaceId,@RequestParam(defaultValue="") Set<String> assets,@RequestParam(defaultValue="") Set<String> entitlements,HttpServletRequest r){return service.publishApplication(tenant(r),workspaceId,id,assets,entitlements);}
    @ExceptionHandler(CompositionService.ScopeViolationException.class) ResponseEntity<ProblemDetail> scope(){return problem(HttpStatus.FORBIDDEN,"SCOPE_VIOLATION","tenant scope does not match authenticated session");}
    @ExceptionHandler(CompositionService.OptimisticConcurrencyException.class) ResponseEntity<ProblemDetail> stale(){return problem(HttpStatus.CONFLICT,"STALE_DRAFT","draft revision is stale or duplicated");}
    @ExceptionHandler(CompositionService.ValidationException.class) ResponseEntity<ProblemDetail> invalid(CompositionService.ValidationException e){ProblemDetail p=ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);p.setTitle("COMPOSITION_NOT_READY");p.setProperty("validation",e.result);return ResponseEntity.unprocessableEntity().body(p);}
    private static ResponseEntity<ProblemDetail> problem(HttpStatus s,String t,String d){ProblemDetail p=ProblemDetail.forStatusAndDetail(s,d);p.setTitle(t);return ResponseEntity.status(s).body(p);}
    private String tenant(HttpServletRequest r){return access.currentTenant();}
}
