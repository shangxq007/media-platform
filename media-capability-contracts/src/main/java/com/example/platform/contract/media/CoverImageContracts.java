package com.example.platform.contract.media;

import com.example.platform.shared.capability.FrameWidth;
import com.example.platform.shared.capability.JpegQuality;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.capability.RasterImageEncoding;
import java.math.BigDecimal;

/**
 * Canonical media.cover-image capability contract.
 *
 * <p>Platform owns this contract. {@link #PROVIDER} is a provider/backend <em>family</em> identity and
 * is deliberately independent of any single capability; a provider declares the capabilities it
 * serves through its manifest
 * ({@link com.example.platform.contract.media.FrameExtractManifest#capabilities()}), and a capability
 * may be served by more than one provider. {@link #CAPABILITY} is one capability of that family —
 * not a provider identity.
 *
 * <p>The subject is a canonical Artifact (no second identity) and the produced cover is committed as
 * an image Artifact related to the subject through {@code ProvenanceRelationType.COVER_OF}.
 */
public final class CoverImageContracts {

    /** Capability served by this slice; one of the provider family's declared capabilities. */
    public static final String CAPABILITY = "media.cover-image";

    /** Capability contract version declared alongside {@link #CAPABILITY}. */
    public static final String CAPABILITY_VERSION = "1.0";

    /**
     * Provider/backend family identity — never a capability identity. The family may declare several
     * capabilities; this slice is the {@link #CAPABILITY} one.
     */
    public static final String PROVIDER = "platform.ffmpeg";

    /**
     * One provider runtime/adapter implementation of {@link #PROVIDER}. Implementation identity is
     * separate from both the family identity and any capability identity.
     */
    public static final String PROVIDER_IMPLEMENTATION = "ffmpeg.cpu.frame-extract.v1";

    public static final String PROVIDER_VERSION = "1.0.0";

    /**
     * Provenance operation tag carried on the single {@code COVER_OF} edge.
     *
     * <p>This is a capability-independent semantic label, never a capability id and never a
     * canonical {@code OperationDefinitionId}. The capability is an independent fact
     * ({@link #CAPABILITY}); the previous value {@code "cover-image:" + CAPABILITY + "@1"} fused the
     * operation, capability and version identities into one string, which is exactly the
     * operation/capability conflation the boundary contract forbids. The provenance contract only
     * requires a non-blank {@code operationId}
     * ({@link com.example.platform.artifact.domain.ArtifactCommitRequest.ProvenanceEdgeDeclaration#operationId()}),
     * so the tag stays a plain slice operation label.
     */
    public static final String OPERATION_ID = "cover-image";

    private CoverImageContracts() {}

    public enum Status { ADMITTED, RUNNING, COMMITTING, COMPLETED, FAILED, CANCELLED }

    /**
     * Immutable cover <em>transport</em> request; the subject is the canonical Artifact identity.
     *
     * <p>This record is a transport DTO: it carries the scope, the target and the wire-shaped
     * parameter fields, and it exposes the typed {@link #capabilityParameters()} authority rather
     * than defining its own parameter contract. The typed parameter value is the shared
     * {@link MediaFrameExtractParametersV1} capability contract
     * ({@code shared.capability}), the same value the thumbnail request derives, so the two slices
     * cannot drift apart. The transport field names/ranges stay unchanged (non-breaking HTTP).
     */
    public record Request(
            String tenantId,
            String projectId,
            String subjectArtifactId,
            double timestampSeconds,
            String imageFormat,
            Integer width,
            Integer quality,
            String idempotencyKey) {

        public Request {
            require(tenantId, "tenantId");
            require(projectId, "projectId");
            require(subjectArtifactId, "subjectArtifactId");
            require(idempotencyKey, "idempotencyKey");
            require(imageFormat, "imageFormat");
            if (!"png".equals(imageFormat) && !"jpeg".equals(imageFormat)) {
                throw new IllegalArgumentException("imageFormat must be png or jpeg");
            }
            if (!Double.isFinite(timestampSeconds) || timestampSeconds < 0) {
                throw new IllegalArgumentException("timestampSeconds must be finite and non-negative");
            }
            if (width != null && (width < 16 || width > 8192)) {
                throw new IllegalArgumentException("width must be within 16..8192");
            }
            if (quality != null && (quality < 1 || quality > 100)) {
                throw new IllegalArgumentException("quality must be within 1..100");
            }
        }

        /**
         * Typed parameter authority for this request (shared {@code media.frame-extract} vocabulary).
         *
         * <p>The transport {@code timestampSeconds} is a {@code double} on the wire; it is decoded
         * through the shortest round-trip decimal string and the exact {@code decimal -> rational}
         * rule ({@link MediaFrameExtractParametersV1#ofExactSeconds}), never through a floating
         * time authority. The encoding/width are the typed capability values, so the slice no
         * longer owns a second parameter vocabulary.
         */
        public MediaFrameExtractParametersV1 capabilityParameters() {
            return MediaFrameExtractParametersV1.ofExactSeconds(
                    BigDecimal.valueOf(timestampSeconds).toPlainString(),
                    encoding(),
                    width == null ? FrameWidth.Native.NATIVE : new FrameWidth.ExplicitPixels(width));
        }

        private RasterImageEncoding encoding() {
            if ("png".equals(imageFormat)) {
                return RasterImageEncoding.Png.PNG;
            }
            return quality == null
                    ? RasterImageEncoding.Jpeg.documentedDefault()
                    : new RasterImageEncoding.Jpeg(new JpegQuality(quality));
        }
    }

    /** Durable task outcome; {@code artifactId} is present only when the cover was committed. */
    public record Result(String taskId, Status status, String artifactId, String failureCode) {}

    static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
