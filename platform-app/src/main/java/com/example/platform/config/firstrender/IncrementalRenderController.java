package com.example.platform.config.firstrender;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Uses the same atomic RenderJob submission, planning, dispatch, lifecycle and result authority. */
@RestController
@RequestMapping("/api/incremental-render")
public class IncrementalRenderController {
    private final FirstRenderSubmissionService submissions;
    private final CanonicalActorResolver actors;
    public IncrementalRenderController(FirstRenderSubmissionService submissions, CanonicalActorResolver actors) {
        this.submissions=submissions; this.actors=actors;
    }
    @PostMapping("/plans")
    public ResponseEntity<FirstRenderPlanHttpResponse> submit(@Valid @RequestBody IncrementalRenderHttpRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        var actor=actors.resolveCurrentActor().map(RenderInitiator::from).orElseThrow(()->new IllegalStateException("Authenticated actor required"));
        var extent=request.extent().toExtent();
        var result=submissions.submitIncremental(request,extent,actor,key);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.response());
    }
}
