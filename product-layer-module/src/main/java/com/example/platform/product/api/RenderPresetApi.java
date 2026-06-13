package com.example.platform.product.api;

import com.example.platform.product.domain.RenderPreset;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for render preset management.
 */
@RestController
@RequestMapping("/api/v1/render/presets")
public class RenderPresetApi {

    private final TemplateService templateService;

    public RenderPresetApi(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Create a new render preset.
     */
    @PostMapping
    public RenderPreset createPreset(@RequestBody CreatePresetRequest request) {
        return templateService.createPreset(request.workspaceId(), request.name(),
                request.description(), request.creatorId(), request.format(),
                request.resolution(), request.profile());
    }

    /**
     * Get preset by ID.
     */
    @GetMapping("/{presetId}")
    public RenderPreset getPreset(@PathVariable String presetId) {
        return templateService.getPreset(presetId);
    }

    /**
     * List presets for a workspace.
     */
    @GetMapping
    public List<RenderPreset> listPresets(@RequestParam String workspaceId) {
        return templateService.listPresets(workspaceId);
    }

    /**
     * Update preset details.
     */
    @PutMapping("/{presetId}")
    public RenderPreset updatePreset(@PathVariable String presetId,
                                      @RequestBody UpdatePresetRequest request) {
        return templateService.updatePreset(presetId, request.name(), request.description());
    }

    /**
     * Update preset render settings.
     */
    @PutMapping("/{presetId}/settings")
    public RenderPreset updatePresetSettings(@PathVariable String presetId,
                                              @RequestBody UpdateSettingsRequest request) {
        return templateService.updatePresetSettings(presetId, request.format(),
                request.resolution(), request.profile(), request.settings());
    }

    // Request records
    public record CreatePresetRequest(String workspaceId, String name, String description,
                                       String creatorId, String format, String resolution,
                                       String profile) {}
    public record UpdatePresetRequest(String name, String description) {}
    public record UpdateSettingsRequest(String format, String resolution, String profile,
                                         Map<String, Object> settings) {}
}
