package com.example.platform.render.ir;
import com.example.platform.shared.time.RationalTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a {@link MediaProjectIr} for structural and semantic correctness.
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>Schema version must be "media-project/v1"</li>
 *   <li>All required top-level objects must be present</li>
 *   <li>All required fields must be non-null and non-blank</li>
 *   <li>Identifiers must be unique within their scope</li>
 *   <li>Exactly one video track is required</li>
 *   <li>Exactly one clip is required (in the single track)</li>
 *   <li>All clip asset references must be declared in the assets list</li>
 *   <li>Source start must be nonnegative</li>
 *   <li>Source duration must be positive</li>
 *   <li>Timeline start must be nonnegative</li>
 *   <li>Output specifications must be declared</li>
 *   <li>Extension keys must be in the supported namespace</li>
 * </ul>
 *
 * <p>Never returns {@code null}. Never throws {@link NullPointerException} as a
 * validation result. Errors are returned in deterministic insertion order.
 */
public final class IrValidator {

    /** Reserved extension namespace prefix. */
    private static final String EXTENSION_NAMESPACE_PREFIX = "com.example.platform.extension.";

    private IrValidator() {}

    /**
     * Validates the given IR, returning a list of errors (empty if valid).
     *
     * @param ir the IR to validate (must not be null)
     * @return an unmodifiable list of validation errors (never null)
     */
    public static List<IrValidationError> validate(MediaProjectIr ir) {
        Objects.requireNonNull(ir, "ir must not be null");
        List<IrValidationError> errors = new ArrayList<>();
        Set<String> outputIds = new LinkedHashSet<>();

        // --- Schema version ---
        if (ir.schemaVersion() == null || ir.schemaVersion().isBlank()) {
            errors.add(IrValidationError.of(
                IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, "$.schemaVersion",
                "schema version is required, expected '" + MediaProjectIr.SCHEMA_VERSION + "'"));
        } else if (!MediaProjectIr.SCHEMA_VERSION.equals(ir.schemaVersion())) {
            errors.add(IrValidationError.of(
                IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, "$.schemaVersion",
                "unsupported schema version '" + ir.schemaVersion()
                + "', expected '" + MediaProjectIr.SCHEMA_VERSION + "'"));
        }

        // --- Project ---
        if (ir.project() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, "$.project", "project is required"));
        } else {
            if (ir.project().id() == null || ir.project().id().isBlank()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.project.id", "project id is required"));
            }
            if (ir.project().name() == null || ir.project().name().isBlank()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.project.name", "project name is required"));
            }
        }

        // --- Assets ---
        Set<String> assetIds = new LinkedHashSet<>();
        if (ir.assets() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, "$.assets", "assets list is required"));
        } else {
            for (int i = 0; i < ir.assets().size(); i++) {
                AssetVersionRef ref = ir.assets().get(i);
                String path = "$.assets[" + i + "]";
                if (ref == null) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path, "asset reference is null"));
                    continue;
                }
                if (ref.assetId() == null || ref.assetId().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".assetId", "assetId is required"));
                }
                if (ref.versionId() == null || ref.versionId().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".versionId", "versionId is required"));
                }
                String assetKey = ref.assetId() + "#" + ref.versionId();
                if (!assetIds.add(assetKey)) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.DUPLICATE_IDENTIFIER, path,
                        "duplicate asset reference: " + assetKey));
                }
            }
        }

        // --- Timeline ---
        if (ir.timeline() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, "$.timeline", "timeline is required"));
        } else {
            if (ir.timeline().id() == null || ir.timeline().id().isBlank()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.timeline.id", "timeline id is required"));
            }
            if (ir.timeline().tracks() == null || ir.timeline().tracks().isEmpty()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.timeline.tracks", "at least one track is required"));
            } else {
                List<VideoTrack> tracks = ir.timeline().tracks();
                if (tracks.size() != 1) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, "$.timeline.tracks",
                        "exactly one video track required, got " + tracks.size()));
                }
                for (int ti = 0; ti < tracks.size(); ti++) {
                    VideoTrack track = tracks.get(ti);
                    String trackPath = "$.timeline.tracks[" + ti + "]";
                    if (track == null) {
                        errors.add(IrValidationError.of(
                            IrErrorCode.VALIDATION_ERROR, trackPath, "track is null"));
                        continue;
                    }
                    if (track.id() == null || track.id().isBlank()) {
                        errors.add(IrValidationError.of(
                            IrErrorCode.VALIDATION_ERROR, trackPath + ".id", "track id is required"));
                    }
                    if (track.clips() == null || track.clips().isEmpty()) {
                        errors.add(IrValidationError.of(
                            IrErrorCode.VALIDATION_ERROR, trackPath + ".clips",
                            "at least one clip is required"));
                    } else if (track.clips().size() != 1) {
                        errors.add(IrValidationError.of(
                            IrErrorCode.VALIDATION_ERROR, trackPath + ".clips",
                            "exactly one clip required, got " + track.clips().size()));
                    }
                    Set<String> clipIds = new LinkedHashSet<>();
                    for (int ci = 0; ci < track.clips().size(); ci++) {
                        Clip clip = track.clips().get(ci);
                        String clipPath = trackPath + ".clips[" + ci + "]";
                        if (clip == null) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.VALIDATION_ERROR, clipPath, "clip is null"));
                            continue;
                        }
                        if (clip.id() == null || clip.id().isBlank()) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.VALIDATION_ERROR, clipPath + ".id", "clip id is required"));
                        }
                        if (!clipIds.add(clip.id())) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.DUPLICATE_IDENTIFIER, clipPath + ".id",
                                "duplicate clip id: " + clip.id()));
                        }
                        // Source range
                        if (clip.source() == null) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.VALIDATION_ERROR, clipPath + ".source", "source is required"));
                        } else {
                            SourceRange src = clip.source();
                            String srcPath = clipPath + ".source";
                            validateSourceRange(src, srcPath, assetIds, errors);
                        }
                        // Timeline start
                        if (clip.timelineStart() == null) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.VALIDATION_ERROR, clipPath + ".timelineStart",
                                "timelineStart is required"));
                        } else {
                            validateTime(clip.timelineStart(), clipPath + ".timelineStart", true, errors);
                        }
                    }
                }
            }
        }

        // --- Outputs ---
        Set<String> outputSpecIds = new LinkedHashSet<>();
        if (ir.outputs() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, "$.outputs", "outputs list is required"));
        } else {
            if (ir.outputs().isEmpty()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.outputs", "at least one output spec is required"));
            }
            for (int i = 0; i < ir.outputs().size(); i++) {
                OutputSpec spec = ir.outputs().get(i);
                String path = "$.outputs[" + i + "]";
                if (spec == null) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path, "output spec is null"));
                    continue;
                }
                if (spec.id() == null || spec.id().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".id", "output spec id is required"));
                }
                if (!outputSpecIds.add(spec.id())) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.DUPLICATE_IDENTIFIER, path + ".id",
                        "duplicate output spec id: " + spec.id()));
                }
                outputIds.add(spec.id());
                if (spec.container() == null || spec.container().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".container", "container is required"));
                }
                if (spec.videoCodec() == null || spec.videoCodec().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".videoCodec", "videoCodec is required"));
                }
                if (spec.width() <= 0) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".width", "width must be positive"));
                }
                if (spec.height() <= 0) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".height", "height must be positive"));
                }
                if (spec.frameRate() == null) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".frameRate", "frameRate is required"));
                } else {
                    validateTime(spec.frameRate(), path + ".frameRate", false, errors);
                    if (!spec.frameRate().isNegative() && spec.frameRate().isZero()) {
                        errors.add(IrValidationError.of(
                            IrErrorCode.INVALID_TIME_VALUE, path + ".frameRate",
                            "frameRate must be positive"));
                    }
                }
                // Validate extensions
                if (spec.extensions() != null) {
                    for (String key : spec.extensions().keySet()) {
                        if (key == null) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.INVALID_EXTENSION, path + ".extensions",
                                "extension key must not be null"));
                            continue;
                        }
                        if (!key.startsWith(EXTENSION_NAMESPACE_PREFIX)) {
                            errors.add(IrValidationError.of(
                                IrErrorCode.UNSUPPORTED_EXTENSION, path + ".extensions." + key,
                                "unsupported extension key: " + key));
                        }
                    }
                    validateExtensionValues(spec.extensions(), path + ".extensions", errors);
                }
            }
        }

        // --- Artifacts ---
        Set<String> artifactIds = new LinkedHashSet<>();
        if (ir.artifacts() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, "$.artifacts", "artifacts list is required"));
        } else {
            if (ir.artifacts().isEmpty()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.VALIDATION_ERROR, "$.artifacts", "at least one artifact is required"));
            }
            for (int i = 0; i < ir.artifacts().size(); i++) {
                ArtifactDeclaration art = ir.artifacts().get(i);
                String path = "$.artifacts[" + i + "]";
                if (art == null) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path, "artifact declaration is null"));
                    continue;
                }
                if (art.id() == null || art.id().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".id", "artifact id is required"));
                }
                if (!artifactIds.add(art.id())) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.DUPLICATE_IDENTIFIER, path + ".id",
                        "duplicate artifact id: " + art.id()));
                }
                if (art.outputSpecId() == null || art.outputSpecId().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".outputSpecId", "outputSpecId is required"));
                } else if (!outputIds.contains(art.outputSpecId())) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.MISSING_OUTPUT_REFERENCE, path + ".outputSpecId",
                        "outputSpecId '" + art.outputSpecId() + "' not declared in outputs"));
                }
                if (art.filename() == null || art.filename().isBlank()) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.VALIDATION_ERROR, path + ".filename", "filename is required"));
                }
            }
        }

        // --- Extensions (top-level) ---
        if (ir.extensions() != null) {
            for (String key : ir.extensions().keySet()) {
                if (key == null) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.INVALID_EXTENSION, "$.extensions",
                        "extension key must not be null"));
                    continue;
                }
                if (!key.startsWith(EXTENSION_NAMESPACE_PREFIX)) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.UNSUPPORTED_EXTENSION, "$.extensions." + key,
                        "unsupported extension key: " + key));
                }
            }
            validateExtensionValues(ir.extensions(), "$.extensions", errors);
        }

        return Collections.unmodifiableList(errors);
    }

    /**
     * Validates and throws if errors exist.
     */
    public static void validateOrThrow(MediaProjectIr ir) {
        List<IrValidationError> errors = validate(ir);
        if (!errors.isEmpty()) {
            throw new IrValidationException(errors);
        }
    }

    private static void validateSourceRange(
        SourceRange src, String path, Set<String> declaredAssetIds,
        List<IrValidationError> errors
    ) {
        if (src.assetRef() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, path + ".assetRef", "assetRef is required"));
        } else {
            String assetKey = src.assetRef().assetId() + "#" + src.assetRef().versionId();
            if (!declaredAssetIds.contains(assetKey)) {
                errors.add(IrValidationError.of(
                    IrErrorCode.MISSING_ASSET_REFERENCE, path + ".assetRef",
                    "asset reference " + assetKey + " not declared in assets"));
            }
        }
        // Start must be nonnegative
        if (src.start() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, path + ".start", "start is required"));
        } else {
            validateTime(src.start(), path + ".start", true, errors);
        }
        // Duration must be positive
        if (src.duration() == null) {
            errors.add(IrValidationError.of(
                IrErrorCode.VALIDATION_ERROR, path + ".duration", "duration is required"));
        } else {
            validateTime(src.duration(), path + ".duration", false, errors);
            if (!src.duration().isNegative() && src.duration().isZero()) {
                errors.add(IrValidationError.of(
                    IrErrorCode.INVALID_TIME_VALUE, path + ".duration",
                    "source duration must be positive"));
            }
        }
    }

    private static void validateTime(
        RationalTime time, String path, boolean allowZero,
        List<IrValidationError> errors
    ) {
        if (time.denominator() <= 0) {
            errors.add(IrValidationError.of(
                IrErrorCode.INVALID_TIME_VALUE, path,
                "denominator must be positive, got: " + time.denominator()));
        }
        if (time.isNegative()) {
            errors.add(IrValidationError.of(
                IrErrorCode.INVALID_TIME_VALUE, path,
                "time value must be nonnegative, got: " + time));
        }
        if (!allowZero && time.isZero()) {
            errors.add(IrValidationError.of(
                IrErrorCode.INVALID_TIME_VALUE, path,
                "time value must be positive, got: " + time));
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateExtensionValues(
        Map<String, Object> extensions, String path,
        List<IrValidationError> errors
    ) {
        for (var entry : extensions.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            validateExtensionValueRecursive(entry.getValue(), path + "." + key, errors);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateExtensionValueRecursive(
        Object value, String path,
        List<IrValidationError> errors
    ) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            if (s.contains("/") || s.contains("\\")) {
                errors.add(IrValidationError.of(
                    IrErrorCode.INVALID_EXTENSION, path,
                    "extension value must not contain path separators: " + s));
            }
            if (s.contains(";") || s.contains("|") || s.contains("`")
                || s.contains("$") || s.contains("&") || s.contains("\n")) {
                errors.add(IrValidationError.of(
                    IrErrorCode.INVALID_EXTENSION, path,
                    "extension value must not contain command-injection characters"));
            }
            if (s.toLowerCase().contains("password") || s.toLowerCase().contains("secret")
                || s.toLowerCase().contains("token") || s.toLowerCase().contains("api_key")
                || s.toLowerCase().contains("key=")) {
                errors.add(IrValidationError.of(
                    IrErrorCode.INVALID_EXTENSION, path,
                    "extension value must not contain credentials or secrets"));
            }
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                validateExtensionValueRecursive(list.get(i), path + "[" + i + "]", errors);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                Object mapKey = entry.getKey();
                if (!(mapKey instanceof String)) {
                    errors.add(IrValidationError.of(
                        IrErrorCode.INVALID_EXTENSION, path,
                        "extension map keys must be strings"));
                    continue;
                }
                validateExtensionValueRecursive(entry.getValue(),
                    path + "." + (String) mapKey, errors);
            }
            return;
        }
        errors.add(IrValidationError.of(
            IrErrorCode.INVALID_EXTENSION, path,
            "unsupported extension value type: " + value.getClass().getSimpleName()));
    }
}
