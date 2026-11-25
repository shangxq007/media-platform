package com.example.platform.config.render;

import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.timeline.canonical.TrackType;
import java.util.HashMap;

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

    /** Check the exact clip and kind of each compiled source edge, including routed embedded audio. */
    static void requireExecutableStreams(TimelineDocument document, RenderGraph graph, String tenant,
            String project, ArtifactSourceFactsQuery sourceFacts) {
        record ClipKey(String track, String clip) {}
        record AuthoredSource(TrackType type, TimelineClip clip) {}
        var clips = new HashMap<ClipKey, AuthoredSource>();
        for (var track : document.getTracks()) {
            for (var clip : track.clips()) {
                clips.put(new ClipKey(track.trackId(), clip.getClipId().value()), new AuthoredSource(track.type(), clip));
            }
        }
        var cache = new HashMap<SourceArtifact, ArtifactSourceFacts>();
        for (var node : graph.nodes()) {
            for (var reference : node.artifactReferences()) {
                if (!(reference instanceof SourceArtifact pin)) continue;
                var path = node.componentPath().segments();
                boolean embedded = path.size() == 3 && path.get(2).equals("embedded-audio");
                if (node.componentPath().kind() != RenderComponentKind.CLIP || (path.size() != 2 && !embedded)) {
                    throw new IllegalArgumentException("Unsupported compiled source path");
                }
                var authored = clips.get(new ClipKey(path.get(0), path.get(1)));
                if (authored == null || !pin.artifactId().value().equals(authored.clip().getArtifactId())
                        || !pin.contentDigest().matches(ContentDigest.sha256(authored.clip().getContentDigest()))) {
                    throw new IllegalArgumentException("Compiled source differs from revision clip pin");
                }
                var kind = switch (authored.type()) {
                    case VIDEO -> embedded ? ArtifactSourceFacts.Kind.AUDIO : ArtifactSourceFacts.Kind.VIDEO;
                    case AUDIO -> ArtifactSourceFacts.Kind.AUDIO;
                    default -> throw new IllegalArgumentException("Unsupported media track kind");
                };
                var facts = cache.computeIfAbsent(pin, key -> sourceFacts.findForExecution(tenant, project,
                        key.artifactId(), key.contentDigest()).orElseThrow(
                                () -> new IllegalArgumentException("Verified source stream facts unavailable")));
                var matches = facts.streams().stream().filter(stream -> stream.kind() == kind).toList();
                if (facts.counts() == null || facts.counts().of(kind) != 1 || matches.size() != 1
                        || !embedded && !matches.getFirst().streamId().equals(authored.clip().getMediaStreamId())) {
                    throw new IllegalArgumentException("Render requires one unambiguous selected source stream of the requested kind");
                }
            }
        }
    }
}
