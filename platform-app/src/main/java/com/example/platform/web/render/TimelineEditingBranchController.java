package com.example.platform.web.render;

import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/render/projects/{projectId}/timeline/branches")
public class TimelineEditingBranchController {
    private final TimelineRevisionQueries queries;
    private final TimelineRevisionCommands commands;
    private final TimelineProjectAuthorizationService authorization;

    public TimelineEditingBranchController(TimelineRevisionQueries queries, TimelineRevisionCommands commands,
                                           TimelineProjectAuthorizationService authorization) {
        this.queries = queries;
        this.commands = commands;
        this.authorization = authorization;
    }

    @GetMapping
    public List<String> list(@PathVariable String projectId) {
        String tenant = TenantContext.get();
        authorization.requireRead(tenant, projectId);
        return queries.listEditingRefs(projectId, tenant);
    }

    @GetMapping("/{refId}")
    public ResponseEntity<BranchView> read(@PathVariable String projectId, @PathVariable String refId) {
        String tenant = TenantContext.get();
        authorization.requireRead(tenant, projectId);
        if (!refId.matches(RevisionRef.EDITING_REF_PATTERN)) {
            throw new IllegalArgumentException("Invalid Timeline ref identity");
        }
        return queries.getBranchHistory(projectId, tenant, refId, 50)
                .map(view -> ResponseEntity.ok(new BranchView(projectId, view.refId(), view.head().id(),
                        view.head().contentHash(), view.history().stream().map(TimelineRevisionController::toListItem).toList())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TimelineRevisionCommands.CreatedEditingRef> create(
            @PathVariable String projectId, @RequestBody CreateBranch body) {
        String tenant = TenantContext.get();
        var actor = authorization.requireWrite(tenant, projectId);
        return commands.createEditingRef(new TimelineMutationContext(tenant, projectId, actor),
                        body.refId(), body.sourceRevisionId(), body.commandId())
                .map(created -> ResponseEntity.status(created.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateBranch(String refId, String sourceRevisionId, String commandId) {}

    public record BranchView(String projectId, String refId, String headRevisionId, String contentHash,
                             List<TimelineRevisionController.RevisionListItem> history) {}
}
