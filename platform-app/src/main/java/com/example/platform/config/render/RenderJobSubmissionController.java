package com.example.platform.config.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.request.RenderInitiator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Product HTTP submission over the shared revision-pinned RenderJob acceptance chain. */
@RestController
@RequestMapping("/api/render-jobs")
public class RenderJobSubmissionController {
    private final RevisionRenderSubmissionService submissions;
    private final CanonicalActorResolver actors;

    public RenderJobSubmissionController(RevisionRenderSubmissionService submissions, CanonicalActorResolver actors) {
        this.submissions = submissions;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<RenderJobSubmissionHttpResponse> submit(
            @Valid @RequestBody RenderJobSubmissionHttpRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        RenderInitiator actor = actors.resolveCurrentActor().map(RenderInitiator::from)
                .orElseThrow(() -> new IllegalStateException("Authenticated render initiator is required"));
        var result = submissions.submit(request, request.extent().toExtent(), actor, key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}
