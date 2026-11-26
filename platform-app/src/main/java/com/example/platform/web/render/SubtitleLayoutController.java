package com.example.platform.web.render;

import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** A read-only projection using the same pinned block layout as formal rendering. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations/subtitle-layout")
public final class SubtitleLayoutController {
    private final TimelineProjectAuthorizationService authorization;

    public SubtitleLayoutController(TimelineProjectAuthorizationService authorization) {
        this.authorization = authorization;
    }

    public record Layout(String text, int width, int fontSize, String fontProfile) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> layout(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody Layout input) {
        authorization.requireRead(tenantId, projectId);
        if (!SubtitleBlockLayout.PROFILE.equals(input.fontProfile())) throw new IllegalArgumentException("Unsupported subtitle font profile");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(SubtitleBlockLayout.png(input.text(), input.width(), input.fontSize()));
    }
}
