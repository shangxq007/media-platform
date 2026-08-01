package com.example.platform.render.ir;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Normalizes a {@link MediaProjectIr} into a deterministic, idempotent form.
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li>Idempotent: normalize(normalize(x)) == normalize(x)</li>
 *   <li>Semantically equivalent inputs produce identical normalized IR</li>
 *   <li>No random IDs, no system time, no env vars, no locale, no timezone dependence</li>
 *   <li>Uses {@link LinkedHashMap} or {@link TreeMap} for deterministic ordering</li>
 * </ul>
 *
 * <h3>Normalization Steps</h3>
 * <ol>
 *   <li>Schema version set to canonical string</li>
 *   <li>Collection ordering: tracks, clips, assets, outputs, artifacts — SEMANTICALLY_ORDERED</li>
 *   <li>Extension keys: CANONICALLY_SORTED (lexicographic)</li>
 *   <li>Time values frozen to canonical rational form</li>
 *   <li>Null extensions → empty map (omitted in output)</li>
 * </ol>
 */
public final class IrNormalizer {

    private IrNormalizer() {}

    /**
     * Returns a normalized copy of the given IR.
     *
     * @param ir the IR to normalize (must not be null)
     * @return a new normalized {@link MediaProjectIr}
     */
    public static MediaProjectIr normalize(MediaProjectIr ir) {
        Objects.requireNonNull(ir, "ir must not be null");

        // Schema version: canonical string
        String schemaVersion = MediaProjectIr.SCHEMA_VERSION;

        // Project: normal form (trim ids)
        Project project = new Project(
            ir.project().id().trim(),
            ir.project().name().trim()
        );

        // Assets: sorted by (assetId, versionId)
        List<AssetVersionRef> assets = new ArrayList<>();
        for (AssetVersionRef ref : ir.assets()) {
            assets.add(new AssetVersionRef(
                ref.assetId().trim(),
                ref.versionId().trim()
            ));
        }
        assets.sort(Comparator.comparing(AssetVersionRef::assetId)
            .thenComparing(AssetVersionRef::versionId));

        // Timeline: normalize tracks and clips
        Timeline timeline = normalizeTimeline(ir.timeline());

        // Outputs: sorted by id
        List<OutputSpec> outputs = new ArrayList<>();
        for (OutputSpec spec : ir.outputs()) {
            outputs.add(normalizeOutputSpec(spec));
        }
        outputs.sort(Comparator.comparing(OutputSpec::id));

        // Artifacts: sorted by id
        List<ArtifactDeclaration> artifacts = new ArrayList<>();
        for (ArtifactDeclaration art : ir.artifacts()) {
            artifacts.add(new ArtifactDeclaration(
                art.id().trim(),
                art.outputSpecId().trim(),
                art.filename().trim()
            ));
        }
        artifacts.sort(Comparator.comparing(ArtifactDeclaration::id));

        // Extensions: sorted lexicographically
        Map<String, Object> extensions = null;
        if (ir.extensions() != null && !ir.extensions().isEmpty()) {
            extensions = new TreeMap<>(ir.extensions());
        }

        return new MediaProjectIr(
            schemaVersion, project, assets, timeline, outputs, artifacts, extensions
        );
    }

    private static Timeline normalizeTimeline(Timeline timeline) {
        List<VideoTrack> tracks = new ArrayList<>();
        for (VideoTrack track : timeline.tracks()) {
            tracks.add(normalizeTrack(track));
        }
        return new Timeline(timeline.id().trim(), tracks);
    }

    private static VideoTrack normalizeTrack(VideoTrack track) {
        List<Clip> clips = new ArrayList<>();
        for (Clip clip : track.clips()) {
            clips.add(normalizeClip(clip));
        }
        return new VideoTrack(track.id().trim(), clips);
    }

    private static Clip normalizeClip(Clip clip) {
        SourceRange source = normalizeSourceRange(clip.source());
        RationalTime timelineStart = normalizeTime(clip.timelineStart());
        return new Clip(clip.id().trim(), source, timelineStart);
    }

    private static SourceRange normalizeSourceRange(SourceRange source) {
        AssetVersionRef ref = new AssetVersionRef(
            source.assetRef().assetId().trim(),
            source.assetRef().versionId().trim()
        );
        return new SourceRange(ref, normalizeTime(source.start()), normalizeTime(source.duration()));
    }

    private static OutputSpec normalizeOutputSpec(OutputSpec spec) {
        Map<String, Object> ext = null;
        if (spec.extensions() != null && !spec.extensions().isEmpty()) {
            ext = new TreeMap<>(spec.extensions());
        }
        return new OutputSpec(
            spec.id().trim(),
            spec.container().trim(),
            spec.videoCodec().trim(),
            spec.width(),
            spec.height(),
            normalizeTime(spec.frameRate()),
            ext
        );
    }

    /**
     * Normalizes a RationalTime to its canonical form by reconstructing it
     * (the constructor does GCD reduction automatically).
     */
    static RationalTime normalizeTime(RationalTime time) {
        return new RationalTime(time.numerator(), time.denominator());
    }
}
