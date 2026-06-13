package com.example.platform.product.api;

import com.example.platform.product.domain.AiSuggestion;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for AI assistant suggestions.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantApi {

    private final AiAssistantService aiAssistantService;

    public AiAssistantApi(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    /**
     * Get timeline layout suggestions.
     */
    @PostMapping("/suggestions/timeline-layout")
    public List<AiSuggestion> suggestTimelineLayout(@RequestBody SuggestTimelineRequest request) {
        return aiAssistantService.suggestTimelineLayout(request.projectId(), request.workspaceId(),
                request.timelineJson(), request.traceId());
    }

    /**
     * Get effect suggestions.
     */
    @PostMapping("/suggestions/effects")
    public List<AiSuggestion> suggestEffects(@RequestBody SuggestEffectsRequest request) {
        return aiAssistantService.suggestEffects(request.projectId(), request.workspaceId(),
                request.timelineJson(), request.currentEffects(), request.traceId());
    }

    /**
     * Get trim optimization suggestions.
     */
    @PostMapping("/suggestions/trim")
    public List<AiSuggestion> suggestTrimOptimization(@RequestBody SuggestTrimRequest request) {
        return aiAssistantService.suggestTrimOptimization(request.projectId(), request.workspaceId(),
                request.timelineJson(), request.traceId());
    }

    /**
     * Get render preset suggestions.
     */
    @PostMapping("/suggestions/render-preset")
    public List<AiSuggestion> suggestRenderPreset(@RequestBody SuggestPresetRequest request) {
        return aiAssistantService.suggestRenderPreset(request.projectId(), request.workspaceId(),
                request.timelineJson(), request.traceId());
    }

    /**
     * Get all suggestions for a project.
     */
    @PostMapping("/suggestions/all")
    public List<AiSuggestion> getAllSuggestions(@RequestBody GetAllSuggestionsRequest request) {
        return aiAssistantService.generateAllSuggestions(request.projectId(), request.workspaceId(),
                request.timelineJson(), request.traceId());
    }

    // Request records
    public record SuggestTimelineRequest(String projectId, String workspaceId,
                                          String timelineJson, String traceId) {}
    public record SuggestEffectsRequest(String projectId, String workspaceId,
                                         String timelineJson, List<String> currentEffects,
                                         String traceId) {}
    public record SuggestTrimRequest(String projectId, String workspaceId,
                                      String timelineJson, String traceId) {}
    public record SuggestPresetRequest(String projectId, String workspaceId,
                                        String timelineJson, String traceId) {}
    public record GetAllSuggestionsRequest(String projectId, String workspaceId,
                                            String timelineJson, String traceId) {}
}
