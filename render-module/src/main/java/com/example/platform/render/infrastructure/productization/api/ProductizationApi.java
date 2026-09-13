package com.example.platform.render.infrastructure.productization.api;

import com.example.platform.render.infrastructure.productization.adaptive.AdaptiveEngine;
import com.example.platform.render.infrastructure.productization.marketplace.Marketplace;
import com.example.platform.render.infrastructure.productization.marketplace.MarketplaceService;
import com.example.platform.identity.api.workspace.WorkspaceCommands;
import com.example.platform.identity.api.workspace.WorkspaceQueries;
import com.example.platform.identity.api.workspace.WorkspaceResponse;
import com.example.platform.identity.api.workspace.WorkspaceMemberResponse;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Productization features.
 */
@RestController
@RequestMapping("/api/product")
public class ProductizationApi {

    private final WorkspaceCommands workspaceCommands;
    private final WorkspaceQueries workspaceQueries;
    private final CanonicalActorResolver actors;
    private final MarketplaceService marketplaceService;
    private final ObjectProvider<AdaptiveEngine> adaptiveEngineProvider;

    public ProductizationApi(
            WorkspaceCommands workspaceCommands, WorkspaceQueries workspaceQueries, CanonicalActorResolver actors,
            MarketplaceService marketplaceService,
            ObjectProvider<AdaptiveEngine> adaptiveEngineProvider) {
        this.workspaceCommands = workspaceCommands;
        this.workspaceQueries = workspaceQueries;
        this.actors = actors;
        this.marketplaceService = marketplaceService;
        this.adaptiveEngineProvider = adaptiveEngineProvider;
    }

    // ─── Workspace Endpoints ───────────────────────────────────────────────────

    @PostMapping("/workspace")
    public WorkspaceView createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        var actor = actors.resolveCurrentActor().orElseThrow(() -> new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication required"));
        if (request.ownerId() != null && !request.ownerId().equals(actor.actorId()))
            throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION, "Owner must be the authenticated actor");
        return view(workspaceCommands.createWorkspace(actor.tenantId(),
                new com.example.platform.identity.api.workspace.CreateWorkspaceRequest(request.name(), request.description(), null)));
    }

    @GetMapping("/workspace/{workspaceId}")
    public WorkspaceView getWorkspace(@PathVariable String workspaceId) {
        return view(workspaceQueries.getWorkspace(workspaceId));
    }

    @GetMapping("/workspace/user/{userId}")
    public List<WorkspaceView> listWorkspacesForUser(@PathVariable String userId) {
        return workspaceQueries.listWorkspacesForUser(userId).stream().map(this::view).toList();
    }

    @PostMapping("/workspace/{workspaceId}/members")
    public WorkspaceView addMember(@PathVariable String workspaceId, @RequestBody AddMemberRequest request) {
        workspaceCommands.addMember(workspaceId,
                new com.example.platform.identity.api.workspace.AddWorkspaceMemberRequest(request.userId(), request.role()));
        return view(workspaceQueries.getWorkspace(workspaceId));
    }

    @GetMapping("/workspace/{workspaceId}/members")
    public List<WorkspaceMemberResponse> members(@PathVariable String workspaceId) {
        return workspaceQueries.listMembers(workspaceId);
    }

    @DeleteMapping("/workspace/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String workspaceId, @PathVariable String userId) {
        workspaceCommands.removeMember(workspaceId, userId);
    }

    // Identity has no Workspace-to-Project sharing or collaboration-session contract.
    // The retired in-memory entries never established canonical access or execution.
    @RequestMapping(path = {"/workspace/{workspaceId}/projects", "/workspace/{workspaceId}/sessions", "/workspace/{workspaceId}/sessions/{sessionId}"}, method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<org.springframework.http.ProblemDetail> retiredCollaboration(@PathVariable String workspaceId) {
        workspaceQueries.getWorkspace(workspaceId);
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.GONE,
                "Product Workspace sharing/session metadata was retired; use canonical Project access. No Workspace-to-Project identity mapping is implied.");
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    // ─── Marketplace Endpoints ─────────────────────────────────────────────────

    @PostMapping("/marketplace/{marketplaceId}/items")
    public Marketplace.MarketplaceItem publishItem(
            @PathVariable String marketplaceId,
            @RequestBody PublishItemRequest request) {
        return marketplaceService.publishItem(
                marketplaceId, request.name(), request.description(),
                request.type(), request.category(), request.authorId(),
                request.authorName(), request.version(), request.tags());
    }

    @GetMapping("/marketplace/{marketplaceId}/search")
    public List<Marketplace.MarketplaceItem> searchItems(
            @PathVariable String marketplaceId,
            @RequestParam String query) {
        return marketplaceService.searchItems(marketplaceId, query);
    }

    @GetMapping("/marketplace/{marketplaceId}/category/{category}")
    public List<Marketplace.MarketplaceItem> getItemsByCategory(
            @PathVariable String marketplaceId,
            @PathVariable String category) {
        return marketplaceService.getItemsByCategory(marketplaceId, category);
    }

    @GetMapping("/marketplace/{marketplaceId}/top-rated")
    public List<Marketplace.MarketplaceItem> getTopRated(
            @PathVariable String marketplaceId,
            @RequestParam(defaultValue = "10") int limit) {
        return marketplaceService.getTopRated(marketplaceId, limit);
    }

    @GetMapping("/marketplace/{marketplaceId}/popular")
    public List<Marketplace.MarketplaceItem> getMostPopular(
            @PathVariable String marketplaceId,
            @RequestParam(defaultValue = "10") int limit) {
        return marketplaceService.getMostPopular(marketplaceId, limit);
    }

    // ─── AI Optimization Endpoints ─────────────────────────────────────────────

    @PostMapping("/optimization/analyze")
    public ResponseEntity<AdaptiveEngine.OptimizationReport> analyzeOptimizations(
            @RequestBody List<AdaptiveEngine.ExecutionTrace> traces) {
        AdaptiveEngine engine = adaptiveEngineProvider.getIfAvailable();
        if (engine == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(engine.analyzeExecutionPatterns(traces));
    }

    // ─── Request/Response Types ────────────────────────────────────────────────

    /** Product wire projection only; no mutable identity, permissions, projects or sessions. */
    public record WorkspaceView(String workspaceId, String tenantId, String name, String description,
            List<WorkspaceMemberResponse> members, String status, java.time.Instant createdAt, java.time.Instant updatedAt) {
        public WorkspaceView { members = List.copyOf(members); }
    }
    private WorkspaceView view(WorkspaceResponse workspace) {
        return new WorkspaceView(workspace.id(), workspace.tenantId(), workspace.name(), workspace.description(),
                workspaceQueries.listMembers(workspace.id()).stream().filter(m -> m.status().equals("ACTIVE")).toList(),
                workspace.status(), workspace.createdAt(), workspace.updatedAt());
    }

    public record CreateWorkspaceRequest(String name, String description, String ownerId) {}
    public record AddMemberRequest(String userId, String role) {}
    public record PublishItemRequest(
            String name, String description,
            Marketplace.MarketplaceItemType type, String category,
            String authorId, String authorName, String version,
            List<String> tags) {}
}
