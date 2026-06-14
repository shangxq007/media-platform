package com.example.platform.render.infrastructure.productization.api;

import com.example.platform.render.infrastructure.productization.adaptive.AdaptiveEngine;
import com.example.platform.render.infrastructure.productization.marketplace.Marketplace;
import com.example.platform.render.infrastructure.productization.marketplace.MarketplaceService;
import com.example.platform.render.infrastructure.productization.workspace.Workspace;
import com.example.platform.render.infrastructure.productization.workspace.ProductWorkspaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Productization features.
 */
@RestController
@RequestMapping("/api/v1/product")
public class ProductizationApi {

    private final ProductWorkspaceService workspaceService;
    private final MarketplaceService marketplaceService;
    private final ObjectProvider<AdaptiveEngine> adaptiveEngineProvider;

    public ProductizationApi(
            ProductWorkspaceService workspaceService,
            MarketplaceService marketplaceService,
            ObjectProvider<AdaptiveEngine> adaptiveEngineProvider) {
        this.workspaceService = workspaceService;
        this.marketplaceService = marketplaceService;
        this.adaptiveEngineProvider = adaptiveEngineProvider;
    }

    // ─── Workspace Endpoints ───────────────────────────────────────────────────

    @PostMapping("/workspace")
    public Workspace createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        return workspaceService.createWorkspace(request.name(), request.description(), request.ownerId());
    }

    @GetMapping("/workspace/{workspaceId}")
    public Workspace getWorkspace(@PathVariable String workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    @GetMapping("/workspace/user/{userId}")
    public List<Workspace> listWorkspacesForUser(@PathVariable String userId) {
        return workspaceService.listWorkspacesForUser(userId);
    }

    @PostMapping("/workspace/{workspaceId}/members")
    public Workspace addMember(@PathVariable String workspaceId, @RequestBody AddMemberRequest request) {
        return workspaceService.addMember(workspaceId, request.userId(), request.role());
    }

    @DeleteMapping("/workspace/{workspaceId}/members/{userId}")
    public Workspace removeMember(@PathVariable String workspaceId, @PathVariable String userId) {
        return workspaceService.removeMember(workspaceId, userId);
    }

    @PostMapping("/workspace/{workspaceId}/projects")
    public Workspace addSharedProject(@PathVariable String workspaceId, @RequestBody AddProjectRequest request) {
        return workspaceService.addSharedProject(workspaceId, request.projectId(), request.projectName(), request.ownerId());
    }

    // ─── Collaboration Endpoints ───────────────────────────────────────────────

    @PostMapping("/workspace/{workspaceId}/sessions")
    public Workspace startSession(@PathVariable String workspaceId, @RequestBody StartSessionRequest request) {
        return workspaceService.startCollaborationSession(workspaceId, request.projectId(), request.participantIds());
    }

    @DeleteMapping("/workspace/{workspaceId}/sessions/{sessionId}")
    public Workspace endSession(@PathVariable String workspaceId, @PathVariable String sessionId) {
        return workspaceService.endCollaborationSession(workspaceId, sessionId);
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

    public record CreateWorkspaceRequest(String name, String description, String ownerId) {}
    public record AddMemberRequest(String userId, Workspace.WorkspaceRole role) {}
    public record AddProjectRequest(String projectId, String projectName, String ownerId) {}
    public record StartSessionRequest(String projectId, List<String> participantIds) {}
    public record PublishItemRequest(
            String name, String description,
            Marketplace.MarketplaceItemType type, String category,
            String authorId, String authorName, String version,
            List<String> tags) {}
}
