package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.EntitlementOverrideService;
import com.example.platform.entitlement.domain.EntitlementOverride;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/entitlements/overrides")
public class EntitlementOverrideController {

    private final EntitlementOverrideService overrideService;

    public EntitlementOverrideController(EntitlementOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    @PostMapping
    public EntitlementOverride createOverride(
            @RequestBody CreateOverrideRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return overrideService.createOverride(
                request.subjectType(), request.subjectId(),
                request.overrideKind(), request.overridePayload(),
                request.effectiveAt(), request.expiresAt(), effectiveActor);
    }

    @GetMapping
    public Map<String, Object> listOverrides(
            @RequestParam(required = false) String subjectId) {
        if (subjectId != null) {
            return Map.of("overrides", overrideService.queryOverrides(subjectId));
        }
        return Map.of("overrides", overrideService.queryOverrides(""));
    }

    @GetMapping("/{id}")
    public EntitlementOverride getOverride(@PathVariable String id) {
        return overrideService.getOverride(id)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + id));
    }

    @PutMapping("/{id}")
    public EntitlementOverride updateOverride(
            @PathVariable String id,
            @RequestBody UpdateOverrideRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return overrideService.updateOverride(
                id, request.overrideKind(), request.overridePayload(),
                request.effectiveAt(), request.expiresAt(), effectiveActor);
    }

    @PostMapping("/{id}/disable")
    public EntitlementOverride disableOverride(
            @PathVariable String id,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return overrideService.disableOverride(id, effectiveActor);
    }

    @PostMapping("/{id}/archive")
    public EntitlementOverride archiveOverride(
            @PathVariable String id,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return overrideService.archiveOverride(id, effectiveActor);
    }

    public record CreateOverrideRequest(
            String subjectType,
            String subjectId,
            String overrideKind,
            String overridePayload,
            Instant effectiveAt,
            Instant expiresAt) {}

    public record UpdateOverrideRequest(
            String overrideKind,
            String overridePayload,
            Instant effectiveAt,
            Instant expiresAt) {}
}
