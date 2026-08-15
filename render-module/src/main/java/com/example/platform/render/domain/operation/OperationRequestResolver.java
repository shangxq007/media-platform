package com.example.platform.render.domain.operation;

import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.render.domain.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.render.domain.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.render.domain.timeline.semantics.selection.ScopeResolver;
import com.example.platform.render.domain.timeline.semantics.selection.SelectionSpec;

import java.util.Objects;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (§3/§33): bounded REQUEST->RESOLVE->INSTANCE.
 * NOT OperationPlan: never generates TimelinePatch, never applies mutations,
 * never builds candidate state, never performs authorization.
 *
 * <p>Hard consistency rule: resolution uses the EXACT immutable base
 * (baseRevisionId + baseContentHash + Timeline state) carried in the request;
 * mismatch between requested base and handed base FAILS CLOSED
 * (STALE_BASE_REVISION). No mutable-latest fallback.
 */
public final class OperationRequestResolver {

    private OperationRequestResolver() {
    }

    /** Exact immutable base context for resolution. */
    public record OperationBaseContext(
            String baseRevisionId,
            String baseContentHash,
            TimelineDocument timeline) {
        public OperationBaseContext {
            Objects.requireNonNull(baseRevisionId, "baseRevisionId");
            Objects.requireNonNull(timeline, "timeline");
        }
    }

    public static OperationInstance resolve(OperationRequest request, OperationBaseContext base)
            throws OperationResolutionException {
        // 1. definition known
        OperationDefinition def = findDefinition(request.definitionId());
        // 2. version supported
        if (!def.version().equals(request.version())) {
            throw new OperationResolutionException(OperationErrorCode.UNSUPPORTED_OPERATION,
                    "definition " + request.definitionId() + " version " + request.version()
                            + " not supported (expected " + def.version() + ")");
        }
        // 3. lifecycle active
        if (def.lifecycle() != OperationDefinition.Lifecycle.ACTIVE) {
            throw new OperationResolutionException(OperationErrorCode.UNSUPPORTED_OPERATION,
                    "definition " + request.definitionId() + " is " + def.lifecycle());
        }
        // 4. exact base binding (fail closed on mismatch)
        if (!base.baseRevisionId().equals(request.baseRevisionId())) {
            throw new OperationResolutionException(OperationErrorCode.STALE_BASE_REVISION,
                    "request base " + request.baseRevisionId() + " != resolver base " + base.baseRevisionId());
        }
        if (request.baseContentHash() != null && !request.baseContentHash().equals(base.baseContentHash())) {
            throw new OperationResolutionException(OperationErrorCode.STALE_BASE_REVISION,
                    "request base content hash mismatch");
        }
        // 5. target type matches definition contract
        OperationTarget resolved = resolveTarget(def, request.target(), base);
        // 6. parameter type matches definition contract
        if (!def.parameterType().isInstance(request.parameters())) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER,
                    "definition " + request.definitionId() + " expects " + def.parameterType().getSimpleName()
                            + " but got " + request.parameters().getClass().getSimpleName());
        }
        // 7. cardinality
        enforceCardinality(def, resolved);
        // 8. operation-level semantic preconditions
        validateSemanticPreconditions(request.definitionId(), request.parameters());
        // 9. digest
        String digest = ParameterDigest.compute(request.definitionId(), request.version(), request.parameters());
        return new OperationInstance(request.definitionId(), request.version(),
                base.baseRevisionId(), base.baseContentHash(), resolved,
                request.parameters(), digest, null);
    }

    static OperationDefinition findDefinition(OperationDefinitionId id) throws OperationResolutionException {
        for (OperationDefinition def : OperationDefinition.V1.ALL) {
            if (def.definitionId().equals(id)) {
                return def;
            }
        }
        throw new OperationResolutionException(OperationErrorCode.UNSUPPORTED_OPERATION,
                "unknown operation definition: " + id);
    }

    private static OperationTarget resolveTarget(OperationDefinition def, OperationTargetRequest target,
                                                 OperationBaseContext base)
            throws OperationResolutionException {
        switch (def.targetKind()) {
            case CLIP_SCOPE -> {
                if (!(target instanceof OperationTargetRequest.ClipSelectionTargetRequest clipReq)) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "definition " + def.definitionId() + " requires clip-scope target");
                }
                var scope = ScopeResolver.resolve(base.timeline(), base.baseRevisionId(), base.baseContentHash(),
                        clipReq.selectionSpec(), clipReq.expansionPolicy());
                return new OperationTarget.ResolvedClipScopeTarget(scope);
            }
            case GROUP -> {
                if (!(target instanceof OperationTargetRequest.GroupTargetRequest groupReq)) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "definition " + def.definitionId() + " requires group target");
                }
                if (!groupExists(base.timeline(), groupReq.groupId())) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "group " + groupReq.groupId() + " does not exist in base revision");
                }
                return new OperationTarget.GroupTarget(groupReq.groupId());
            }
            case SYNC -> {
                if (!(target instanceof OperationTargetRequest.SyncTargetRequest syncReq)) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "definition " + def.definitionId() + " requires sync target");
                }
                boolean needsExisting = !def.definitionId().value().equals("timeline.sync.create");
                if (needsExisting && !syncExists(base.timeline(), syncReq.syncIdentityKey())) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "sync " + syncReq.syncIdentityKey() + " does not exist in base revision");
                }
                return new OperationTarget.SyncTarget(syncReq.syncIdentityKey());
            }
            case AUDIO -> {
                if (!(target instanceof OperationTargetRequest.AudioTargetRequest audioReq)) {
                    throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                            "definition " + def.definitionId() + " requires audio target");
                }
                return new OperationTarget.AudioTarget(audioReq.audioMixInput());
            }
        }
        throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE, "unresolved target kind");
    }

    static boolean groupExists(TimelineDocument doc, com.example.platform.render.domain.timeline.semantics.relationship.GroupId gid) {
        for (SemanticRelationship rel : doc.getSemanticRelationships()) {
            if (rel instanceof GroupRelationship g && g.groupId().equals(gid)) {
                return true;
            }
        }
        return false;
    }

    static boolean syncExists(TimelineDocument doc, String identityKey) {
        for (SemanticRelationship rel : doc.getSemanticRelationships()) {
            if (rel instanceof SyncRelationship s && s.identityKey().equals(identityKey)) {
                return true;
            }
        }
        return false;
    }

    private static void enforceCardinality(OperationDefinition def, OperationTarget target)
            throws OperationResolutionException {
        int size = switch (target) {
            case OperationTarget.ResolvedClipScopeTarget r -> r.resolvedScope().resolvedClipIds().size();
            case OperationTarget.GroupTarget g -> 1;
            case OperationTarget.SyncTarget s -> 1;
            case OperationTarget.AudioTarget a -> 1;
        };
        if (size < def.minCardinality() || size > def.maxCardinality()) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_SCOPE,
                    "definition " + def.definitionId() + " cardinality " + size
                            + " outside [" + def.minCardinality() + "," + def.maxCardinality() + "]");
        }
    }

    private static void validateSemanticPreconditions(OperationDefinitionId id, OperationParameters p)
            throws OperationResolutionException {
        if (id.value().equals("timeline.temporal.set-rate") && !(p instanceof OperationParameters.SetTemporalRateParameters)) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER, "set-rate requires rate parameter");
        }
        if (id.value().equals("timeline.temporal.set-direction") && !(p instanceof OperationParameters.SetTemporalDirectionParameters)) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER, "set-direction requires direction parameter");
        }
        if (id.value().equals("timeline.temporal.freeze") && !(p instanceof OperationParameters.FreezeParameters)) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER, "freeze requires sourcePosition parameter");
        }
        if (id.value().equals("timeline.group.create")
                && p instanceof OperationParameters.CreateGroupParameters cg
                && cg.members().size() < 2) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER, "group requires >=2 members");
        }
        if (id.value().equals("timeline.sync.create")
                && p instanceof OperationParameters.CreateSyncParameters cs
                && cs.endpointA().equals(cs.endpointB())) {
            throw new OperationResolutionException(OperationErrorCode.INVALID_PARAMETER, "sync endpoints must be distinct");
        }
    }
}
