package com.example.platform.execution.planning;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.shared.capability.*;
import java.util.Objects;

/** Artifact-sourced input to #21. A scoped request identity is supplied by Execution admission. */
public record ArtifactCapabilityRequest(String requestIdentity, String capabilityId,
        ArtifactPin source, MediaFrameExtractParametersV1 parameters) {
    /** Platform parameter default, pinned before fingerprinting or provider selection. */
    public static final int DEFAULT_JPEG_QUALITY = 85;
    public ArtifactCapabilityRequest {
        if (requestIdentity == null || !requestIdentity.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("scoped request identity required");
        }
        if (!"media.cover-image".equals(capabilityId) && !"media.thumbnail".equals(capabilityId)) {
            throw new IllegalArgumentException("unsupported Artifact-sourced capability");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.position().ticks() < 0) throw new IllegalArgumentException("negative source position");
        if (parameters.width() instanceof FrameWidth.ExplicitPixels width
                && width.pixels() > ("media.thumbnail".equals(capabilityId) ? 4096 : 8192)) {
            throw new IllegalArgumentException("frame width exceeds capability envelope");
        }
        if (parameters.encoding() instanceof RasterImageEncoding.Jpeg jpeg && jpeg.quality().isEmpty()) {
            parameters = new MediaFrameExtractParametersV1(parameters.position(),
                    new RasterImageEncoding.Jpeg(new JpegQuality(DEFAULT_JPEG_QUALITY)), parameters.width());
        }
    }
    public String canonical() {
        return new CanonicalWriter().tag("ARTIFACT_CAPABILITY_REQUEST_V1")
                .field("requestIdentity", requestIdentity).field("capabilityId", capabilityId)
                .field("sourceArtifact", source.artifactId().value())
                .field("sourceDigest", source.contentDigest().canonicalValue())
                .exactTime(parameters.position().ticks(), parameters.position().timeScale())
                .field("encoding", parameters.encoding() instanceof RasterImageEncoding.Png ? "png" : "jpeg")
                .field("quality", parameters.encoding() instanceof RasterImageEncoding.Jpeg jpeg
                        ? Integer.toString(jpeg.quality().orElseThrow().value()) : "")
                .field("width", parameters.width() instanceof FrameWidth.ExplicitPixels width
                        ? Integer.toString(width.pixels()) : "native").build();
    }
}
