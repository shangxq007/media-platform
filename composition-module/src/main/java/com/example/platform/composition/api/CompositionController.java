package com.example.platform.composition.api;

import com.example.platform.composition.app.CompositionService;
import com.example.platform.composition.domain.CompositionModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/composition")
public class CompositionController {
    private final CompositionService service;
    public CompositionController(CompositionService service){this.service=service;}
    @GetMapping("/capabilities") public List<CapabilityAvailability> catalog(){return service.catalog();}
    @PostMapping("/workflows/drafts") public TemplateWorkflow saveWorkflow(@RequestBody TemplateWorkflow v,HttpServletRequest r){return service.saveWorkflow(v,tenant(r));}
    @GetMapping("/workflows/drafts/{id}") public TemplateWorkflow workflow(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.workflow(tenant(r),workspaceId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @PostMapping("/workflows/validate") public ValidationResult validateWorkflow(@RequestBody TemplateWorkflow v,@RequestParam(defaultValue="") Set<String> assets,HttpServletRequest r){return service.validateWorkflow(v,tenant(r),assets);}
    @PostMapping("/workflows/drafts/{id}/publish") public TemplateWorkflow publishWorkflow(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.publishWorkflow(tenant(r),workspaceId,id);}
    @PostMapping("/applications/drafts") public Application saveApplication(@RequestBody Application v,HttpServletRequest r){return service.saveApplication(v,tenant(r));}
    @GetMapping("/applications/drafts/{id}") public Application application(@PathVariable String id,@RequestParam String workspaceId,HttpServletRequest r){return service.application(tenant(r),workspaceId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @PostMapping("/applications/validate") public ValidationResult validateApplication(@RequestBody Application v,@RequestParam(defaultValue="") Set<String> assets,@RequestParam(defaultValue="") Set<String> entitlements,HttpServletRequest r){return service.validateApplication(v,tenant(r),assets,entitlements);}
    @PostMapping("/applications/drafts/{id}/publish") public Application publishApplication(@PathVariable String id,@RequestParam String workspaceId,@RequestParam(defaultValue="") Set<String> assets,@RequestParam(defaultValue="") Set<String> entitlements,HttpServletRequest r){return service.publishApplication(tenant(r),workspaceId,id,assets,entitlements);}
    @ExceptionHandler(CompositionService.ScopeViolationException.class) ResponseEntity<ProblemDetail> scope(){return problem(HttpStatus.FORBIDDEN,"SCOPE_VIOLATION","tenant scope does not match authenticated session");}
    @ExceptionHandler(CompositionService.OptimisticConcurrencyException.class) ResponseEntity<ProblemDetail> stale(){return problem(HttpStatus.CONFLICT,"STALE_DRAFT","draft revision is stale or duplicated");}
    @ExceptionHandler(CompositionService.ValidationException.class) ResponseEntity<ProblemDetail> invalid(CompositionService.ValidationException e){ProblemDetail p=ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);p.setTitle("COMPOSITION_NOT_READY");p.setProperty("validation",e.result);return ResponseEntity.unprocessableEntity().body(p);}
    private static ResponseEntity<ProblemDetail> problem(HttpStatus s,String t,String d){ProblemDetail p=ProblemDetail.forStatusAndDetail(s,d);p.setTitle(t);return ResponseEntity.status(s).body(p);}
    private static String tenant(HttpServletRequest r){Object v=r.getAttribute("jwt.tenantId"); if(v==null || v.toString().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); return v.toString();}
}
