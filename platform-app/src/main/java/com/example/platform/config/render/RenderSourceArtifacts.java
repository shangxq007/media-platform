package com.example.platform.config.render;

import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.audio.domain.mix.AudioMixInput;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts the distinct source {@link ArtifactId}s referenced by a canonical
 * {@link TimelineDocument}'s clips.
 *
 * <p>Read-only projection over the canonical timeline accessors; with immutable facts resolved by exact pin during admission. Deduplication is order-preserving so the result is deterministic.
 */
public final class RenderSourceArtifacts {

    private RenderSourceArtifacts() {
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

    /** Native render inputs identify a single video or audio stream of their pinned Artifact. */
    static void requireExecutableStreams(TimelineDocument document, String tenant, String project,
            ArtifactSourceFactsQuery sourceFacts) {
        record Pin(ArtifactId id, ContentDigest digest) {}
        var cache = new HashMap<Pin, ArtifactSourceFacts>();
        var routes = document.getAudioMix().routes().stream().map(route -> route.input())
                .collect(Collectors.toSet());
        for (TimelineTrack track : document.getTracks()) {
            for (TimelineClip clip : track.clips()) {
                var pin = new Pin(new ArtifactId(clip.getArtifactId()),
                        ContentDigest.sha256(clip.getContentDigest()));
                var facts = cache.computeIfAbsent(pin, key -> sourceFacts.findForExecution(tenant, project, key.id(), key.digest())
                        .orElseThrow(() -> new IllegalArgumentException("Verified source stream facts unavailable")));
                var kind = switch (track.type()) {
                    case AUDIO -> ArtifactSourceFacts.Kind.AUDIO;
                    case VIDEO -> ArtifactSourceFacts.Kind.VIDEO;
                    default -> throw new IllegalArgumentException("Unsupported media track kind");
                };
                var matches = facts.streams().stream().filter(stream -> stream.kind() == kind).toList();
                if (facts.counts() == null || facts.counts().of(kind) != 1 || matches.size() != 1 || !matches.getFirst().streamId().equals(clip.getMediaStreamId())) {
                    throw new IllegalArgumentException("Render requires one unambiguous selected source stream of the requested kind");
                }
                if (kind == ArtifactSourceFacts.Kind.VIDEO
                        && routes.contains(AudioMixInput.of(
                                track.trackId(), clip.getClipId().value()))
                        && (facts.counts().audio() != 1 || facts.streams().stream().filter(stream -> stream.kind()
                                == ArtifactSourceFacts.Kind.AUDIO).count() != 1)) {
                    throw new IllegalArgumentException("Embedded audio route requires one unambiguous audio stream");
                }
            }
        }
    }
}
