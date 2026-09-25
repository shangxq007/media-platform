package com.example.platform.coverimage;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Stable typed media.cover-image invocation contract. Provider-specific mechanics (FFmpeg, sandbox
 * toolchain, argument shaping) stay behind this boundary.
 */
public interface CoverImageCapabilityProvider {

    Manifest manifest();

    /**
     * Renders one cover image from an already digest-verified subject artifact.
     *
     * @param request   canonical cover request
     * @param inputPath path of the digest-verified subject artifact bytes
     * @param cancelled cancellation probe owned by the caller
     */
    Result render(CoverImageContracts.Request request, Path inputPath, BooleanSupplier cancelled);

    record Manifest(
            String capabilityId,
            String providerId,
            String providerVersion,
            String executableToolchain,
            Set<String> inputFormats,
            Set<String> outputFormats,
            double minimumTimestampSeconds,
            double maximumTimestampSeconds,
            int minimumWidth,
            int maximumWidth,
            long maximumInputBytes,
            int timeoutSeconds,
            String trustRequirement,
            String runtimeRequirement) {}

    record Result(byte[] bytes, String contentType, String failureCode) {

        public static Result success(byte[] bytes, String contentType) {
            return new Result(bytes, contentType, null);
        }

        public static Result failure(String failureCode) {
            return new Result(null, null, failureCode);
        }

        public boolean succeeded() {
            return bytes != null && failureCode == null;
        }
    }
}
