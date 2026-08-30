package com.example.platform.render.api;

import com.example.platform.render.app.autocaptions.AutoCaptionsService;
import com.example.platform.render.app.autocaptions.AutoCaptionsService.AutoCaptionsRequest;
import com.example.platform.render.app.autocaptions.AutoCaptionsService.AutoCaptionsResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/render/auto-captions")
public class AutoCaptionsController {

    private final AutoCaptionsService autoCaptionsService;

    public AutoCaptionsController(AutoCaptionsService autoCaptionsService) {
        this.autoCaptionsService = autoCaptionsService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> generateCaptions(
            @RequestBody GenerateCaptionsRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }

        AutoCaptionsResult result = autoCaptionsService.generateCaptions(new AutoCaptionsRequest(
                effectiveTenant,
                request.projectId(),
                request.assetId(),
                request.audioUri(),
                request.language(),
                request.maxSegmentDurationMs() > 0 ? request.maxSegmentDurationMs() : 10000,
                request.fontFamily(),
                request.fontSize() > 0 ? request.fontSize() : 24,
                request.fontColor() != null ? request.fontColor() : "#FFFFFF",
                request.positionX(),
                request.positionY()));

        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "projectId", result.projectId(),
                    "success", false,
                    "errorCode", result.errorCode(),
                    "error", result.error()));
        }

        return ResponseEntity.ok(Map.of(
                "projectId", result.projectId(),
                "success", true,
                "segmentCount", result.segmentCount(),
                "overlays", result.overlays().stream()
                        .map(o -> Map.<String, Object>of(
                                "id", o.id(),
                                "text", o.text(),
                                "startTime", o.startTime(),
                                "duration", o.duration(),
                                "fontFamily", o.fontFamily().value(),
                                "fontSize", o.fontSize(),
                                "color", o.color()))
                        .toList()));
    }

    public record GenerateCaptionsRequest(
            String projectId,
            String assetId,
            String audioUri,
            String language,
            int maxSegmentDurationMs,
            com.example.platform.fonttext.typography.FontFamilyName fontFamily,
            int fontSize,
            String fontColor,
            double positionX,
            double positionY) {}
}
