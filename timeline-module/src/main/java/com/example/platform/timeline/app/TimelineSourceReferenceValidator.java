package com.example.platform.timeline.app;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;

import com.example.platform.media.api.MediaAssetQueries;
import com.example.platform.media.api.MediaStreamQueries;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.media.domain.stream.StreamKind;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TIMELINE_SOURCE_REFERENCE_VALIDITY_V1 (T13) — bounded validation port.
 *
 * <p>Timeline validates the BINDING against canonical media source truth through the
 * media domain's typed query contract (MediaAssetQueries / MediaStreamQueries).
 * Timeline never reads media DB rows, never owns media truth.
 *
 * <p>Boundary notes:
 * <ul>
 *   <li>This port checks source asset ownership, stream membership, and track kind.
 *       Canonical revision commitment separately validates Artifact pins through its
 *       existing Artifact owner path. A successful Media lookup is not Artifact acceptance.</li>
 *   <li>Context-bound authoring supplies tenant, project, and track kind; context-free
 *       validation only checks structural references and grants no mutation authority.</li>
 * </ul>
 */
@Component
public class TimelineSourceReferenceValidator implements TimelineSourceValidation {

    private final MediaAssetQueries mediaAssetRepository;
    private final MediaStreamQueries mediaStreamRepository;

    public TimelineSourceReferenceValidator(
            MediaAssetQueries mediaAssetRepository,
            MediaStreamQueries mediaStreamRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaStreamRepository = mediaStreamRepository;
    }

    public ValidationResult validate(MediaStreamSourceBinding binding) {
        return validate(binding, null, null, null);
    }

    /**
     * Context-bound source resolution for canonical authoring. It proves that
     * the exact immutable binding names an existing Media-owned asset/stream
     * in the target ownership scope and that the stream kind is compatible
     * with the target Timeline track. It never resolves mutable latest media.
     */
    public ValidationResult validate(MediaStreamSourceBinding binding,
                                     String expectedTenantId,
                                     String expectedProjectId,
                                     TrackType expectedTrackType) {
        List<String> violations = new ArrayList<>();

        MediaAssetId assetId = binding.mediaAssetId();
        var asset = mediaAssetRepository.findById(assetId);
        if (asset.isEmpty()) {
            violations.add("MediaAssetId does not exist: " + assetId.value());
        } else {
            if (expectedTenantId != null && !expectedTenantId.equals(asset.get().tenantId())) {
                violations.add("MediaAssetId is outside target tenant: " + assetId.value());
            }
            if (expectedProjectId != null && !expectedProjectId.equals(asset.get().projectId())) {
                violations.add("MediaAssetId is outside target project: " + assetId.value());
            }
            var stream = mediaStreamRepository.findByMediaAssetId(assetId).stream()
                    .filter(s -> s.id().equals(binding.mediaStreamId()))
                    .findFirst();
            if (stream.isEmpty()) {
                violations.add("MediaStreamId does not belong to MediaAsset: "
                        + binding.mediaStreamId().value() + " / " + assetId.value());
            } else if (expectedTrackType != null
                    && !compatible(expectedTrackType, stream.get().kind())) {
                violations.add("MediaStream kind " + stream.get().kind()
                        + " is incompatible with target track " + expectedTrackType);
            }
        }

        // Artifact pin acceptance remains at the canonical revision owner boundary.
        // ContentDigest format is validated at ContentDigest construction.
        // Exact source range validity (start <= end, non-negative) is type-guaranteed by
        // MediaClip.TimeRange and MediaTime invariants.

        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    private static boolean compatible(TrackType trackType, StreamKind streamKind) {
        return (trackType == TrackType.VIDEO && streamKind == StreamKind.VIDEO)
                || (trackType == TrackType.AUDIO && streamKind == StreamKind.AUDIO);
    }


}
