package com.example.platform.web.render;

import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.fonttext.typography.SubtitleTextRange;
import com.example.platform.operation.application.SubtitleFontPolicy;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** A read-only projection using the same pinned block layout as formal rendering. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations")
public final class SubtitleLayoutController {
    private final TimelineProjectAuthorizationService authorization;

    private final SubtitleFontPolicy fonts;

    public SubtitleLayoutController(TimelineProjectAuthorizationService authorization, SubtitleFontPolicy fonts) {
        this.authorization = authorization;
        this.fonts = fonts;
    }

    public record CatalogEntry(String id, String label, String family, String style) {}

    @GetMapping("/subtitle-fonts")
    public ResponseEntity<List<CatalogEntry>> catalog(@PathVariable String tenantId, @PathVariable String projectId) {
        authorization.requireRead(tenantId, projectId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fonts.selectable().stream()
                .map(font -> new CatalogEntry(font.id(), font.label(), font.family(), font.style())).toList());
    }

    public record Layout(String text, int width, int fontSize, String fontProfile, Integer destinationWidth, List<com.example.platform.fonttext.typography.SubtitleFontRun> fontRuns, SubtitleTextRange selection,
            boolean paintSelected) {}

    @PostMapping(path = "/subtitle-layout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> layout(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody Layout input) {
        authorization.requireRead(tenantId, projectId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                    .body(SubtitleBlockLayout.png(input.text(), input.width(), input.fontSize(), input.fontProfile(),
                        input.destinationWidth() == null ? SubtitleBlockLayout.REFERENCE_WIDTH : input.destinationWidth(),
                        input.fontRuns() == null ? List.of() : input.fontRuns(), input.selection(), input.paintSelected()));
    }
}
