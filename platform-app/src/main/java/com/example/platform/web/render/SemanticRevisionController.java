package com.example.platform.web.render;

import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Stable references scoped by an authorized project, immutable revision and digest. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/semantic/revisions/{revisionId}/objects")
public final class SemanticRevisionController {
    public enum Kind { TRACK, CLIP, EFFECT, TRANSITION, AUDIO_ROUTE, TEXT_ELEMENT, AUTOMATION, GROUP, SYNC, ALIGNMENT }
    public record Item(String id, String parentId, Map<String, ?> metadata) {}
    public record Page(String revisionId, String contentHash, Kind kind, List<Item> items, Integer nextOffset) {}

    private final TimelineProjectAuthorizationService authorization;
    private final TimelineRevisionCommands revisions;
    private final EffectSemanticSnapshotStore effects;

    public SemanticRevisionController(TimelineProjectAuthorizationService authorization,
            TimelineRevisionCommands revisions, EffectSemanticSnapshotStore effects) {
        this.authorization = authorization;
        this.revisions = revisions;
        this.effects = effects;
    }

    @GetMapping("/{kind}")
    public Page objects(@PathVariable String tenantId, @PathVariable String projectId,
            @PathVariable String revisionId, @PathVariable Kind kind, @RequestParam String contentHash,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String id, @RequestParam(required = false) String parentId) {
        authorization.requireRead(tenantId, projectId);
        if (offset < 0 || limit < 1 || limit > 100 || (long) offset + limit > Integer.MAX_VALUE
                || (id != null && (id.isBlank() || offset != 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid semantic reference or page");
        }
        var revision = revisions.findById(tenantId, revisionId);
        if (revision == null || !projectId.equals(revision.productId())) throw unavailable();
        if (!revision.semanticContext().timelineContentDigest().equals(contentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revision content identity mismatch");
        }
        var document = revisions.findPayloadDocument(tenantId, revisionId).orElseThrow(SemanticRevisionController::unavailable);
        Stream<Item> entries;
        if (kind == Kind.EFFECT) {
            var pin = revision.effectSemanticSnapshotReference();
            var snapshot = effects.findById(projectId, tenantId, pin.snapshotId())
                    .filter(s -> s.reference().equals(pin)).orElseThrow(SemanticRevisionController::unavailable);
            var authored = snapshot.entries().stream().map(e -> e.effectInstanceId()).collect(java.util.stream.Collectors.toSet());
            var clipEffects = document.getTracks().stream().flatMap(track -> track.clips().stream().flatMap(clip ->
                    clip.getEffects().stream().filter(effect -> !authored.contains(effect.id())).map(effect ->
                            new Item(effect.id(), clip.getClipId().value(), Map.of("trackId", track.trackId(),
                                    "definitionId", effect.effectKey(), "parameters", effect.parameters())))));
            entries = Stream.concat(effectItems(snapshot), clipEffects);
        } else {
            entries = items(document, kind);
        }
        var window = entries.filter(item -> parentId == null || parentId.equals(item.parentId()))
                .filter(item -> id == null || id.equals(item.id())).skip(offset)
                .limit(id == null ? limit + 1L : 2L).toList();
        if (id != null && window.isEmpty()) throw unavailable();
        if (id != null && window.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiguous semantic reference");
        }
        boolean more = window.size() > limit;
        return new Page(revisionId, contentHash, kind, more ? window.subList(0, limit) : window,
                more ? offset + limit : null);
    }

    private static Stream<Item> items(TimelineDocument document, Kind kind) {
        return switch (kind) {
            case TRACK -> document.getTracks().stream().map(track -> new Item(track.trackId(), null,
                    Map.of("type", track.type(), "clipCount", track.clips().size())));
            case CLIP -> document.getTracks().stream().flatMap(track -> track.clips().stream().map(clip ->
                    new Item(clip.getClipId().value(), track.trackId(), Map.of("sourceKind", clip.getSourceKind()))));
            case TRANSITION -> document.getTransitions().stream().map(t -> new Item(t.transitionId(), null,
                    Map.of("definitionId", t.transitionDefinitionId(), "version", t.transitionDefinitionVersion(),
                            "outgoingClipId", t.outgoingClipId(), "incomingClipId", t.incomingClipId(),
                            "alignment", t.alignment(), "temporalPolicy", t.temporalPolicy(), "parameters", t.parameters())));
            case AUDIO_ROUTE -> document.getAudioMix().routes().stream().map(route ->
                    new Item(route.input().clipId(), route.input().trackId(), Map.of("muted", route.mute().muted(), "gain", route.gain().linear(), "balance", route.balance().value())));
            case TEXT_ELEMENT -> document.getTextElements().stream().map(text ->
                    new Item(text.id().value(), null, Map.of()));
            case AUTOMATION -> document.getAutomations().stream().map(curve -> new Item(curve.automationId(),
                    curve.targetEntityId(), Map.of("parameterPath", curve.parameterPath(), "valueType", curve.valueType(),
                            "extrapolation", curve.extrapolation(), "keyframeCount", curve.keyframes().size())));
            case GROUP, SYNC, ALIGNMENT -> document.getSemanticRelationships().stream()
                    .filter(r -> r.kind().name().equals(kind.name())).map(r -> switch (r) {
                        case com.example.platform.timeline.semantics.relationship.GroupRelationship group ->
                                new Item(group.groupId().value(), null, Map.of("members", group.members().stream().map(id -> id.value()).sorted().toList()));
                        case com.example.platform.timeline.semantics.relationship.SyncRelationship sync ->
                                new Item(sync.identityKey(), null, Map.of("endpointA", sync.endpointA().value(), "endpointB", sync.endpointB().value()));
                        case com.example.platform.timeline.semantics.relationship.AlignmentRelationship alignment ->
                                new Item(alignment.identityKey(), null, Map.of());
                        default -> throw unavailable();
                    });
            case EFFECT -> throw new IllegalStateException("Effect references require the pinned semantic snapshot");
        };
    }

    private static Stream<Item> effectItems(EffectSemanticSnapshot snapshot) {
        return snapshot.entries().stream().map(entry -> {
            ClipEffectTarget target = (ClipEffectTarget) entry.target();
            return new Item(entry.effectInstanceId(), target.clipId(), Map.of("trackId", target.trackId(),
                    "enabled", entry.enabled(), "definition", entry.definitionSnapshot(), "parameters", entry.parameters()));
        });
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Semantic reference unavailable");
    }
}
