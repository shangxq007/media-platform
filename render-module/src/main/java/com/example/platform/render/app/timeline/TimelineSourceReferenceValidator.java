package com.example.platform.render.app.timeline;

import com.example.platform.media.app.MediaAssetRepository;
import com.example.platform.media.app.MediaStreamRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.render.domain.timeline.semantics.clip.SourceBinding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TIMELINE_SOURCE_REFERENCE_VALIDITY_V1 (T13) — bounded validation port.
 *
 * <p>Timeline validates the BINDING against canonical media source truth through the
 * media domain's typed query contract (MediaAssetRepository / MediaStreamRepository).
 * Timeline never reads media DB rows, never owns media truth.
 *
 * <p>Boundary notes:
 * <ul>
 *   <li>artifact pin (artifactId/contentDigest) existence check is DEFERRED: the current
 *       dependency graph is artifact-module -&gt; render-module, so render cannot query the
 *       artifact catalog without a cycle; the pin structure is enforced by SourceBinding
 *       invariants (non-null, typed).</li>
 *   <li>stream kind compatibility is left to clip semantics (TimelineClip has no kind
 *       field); MediaStreamId must belong to the pinned MediaAsset.</li>
 * </ul>
 */
@Component
public class TimelineSourceReferenceValidator {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaStreamRepository mediaStreamRepository;

    public TimelineSourceReferenceValidator(
            MediaAssetRepository mediaAssetRepository,
            MediaStreamRepository mediaStreamRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaStreamRepository = mediaStreamRepository;
    }

    public ValidationResult validate(SourceBinding binding) {
        List<String> violations = new ArrayList<>();

        MediaAssetId assetId = binding.mediaAssetId();
        if (!mediaAssetRepository.exists(assetId)) {
            violations.add("MediaAssetId does not exist: " + assetId.value());
        } else {
            boolean streamBelongsToAsset = mediaStreamRepository
                    .findByMediaAssetId(assetId).stream()
                    .anyMatch(s -> s.id().equals(binding.mediaStreamId()));
            if (!streamBelongsToAsset) {
                violations.add("MediaStreamId does not belong to MediaAsset: "
                        + binding.mediaStreamId().value() + " / " + assetId.value());
            }
        }

        // Artifact pin existence: DEFERRED (dependency-cycle constraint, see class javadoc).
        // ContentDigest format is validated at ContentDigest construction.
        // Exact source range validity (start <= end, non-negative) is type-guaranteed by
        // MediaClip.TimeRange and MediaTime invariants.

        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
