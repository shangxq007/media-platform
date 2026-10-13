package com.example.platform.config.firstrender;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure helper: extracts the distinct source {@link ArtifactId}s referenced by a canonical
 * {@link TimelineDocument}'s clips.
 *
 * <p>Read-only projection over the canonical timeline accessors; it performs no resolution, no
 * lookup and no mutation. Deduplication is order-preserving so the result is deterministic.
 */
public final class FirstRenderSourceArtifacts {

    private FirstRenderSourceArtifacts() {
    }

    /** The distinct, order-preserving source artifact ids declared by the document's clips. */
    public static List<ArtifactId> artifactIds(TimelineDocument document) {
        Objects.requireNonNull(document, "document");
        Set<ArtifactId> ids = new LinkedHashSet<>();
        for (TimelineTrack track : document.getTracks()) {
            for (TimelineClip clip : track.clips()) {
                String artifactId = clip.getArtifactId();
                if (artifactId != null && !artifactId.isBlank()) {
                    ids.add(new ArtifactId(artifactId));
                }
            }
        }
        return List.copyOf(ids);
    }
}
