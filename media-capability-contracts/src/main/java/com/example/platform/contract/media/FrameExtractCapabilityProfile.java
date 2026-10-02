package com.example.platform.contract.media;

import java.util.Objects;

/**
 * Per-capability execution profile of the merged frame-extract provider.
 *
 * <p>The provider family {@code platform.ffmpeg} / implementation
 * {@code ffmpeg.cpu.frame-extract.v1} is capability-neutral. What actually differs when it serves
 * {@code media.cover-image} versus {@code media.thumbnail} — the accepted input form, the width
 * bound, the timeout and which sandbox task capability the bytes run through — is carried here and
 * selected by the {@code capabilityId} the caller passes in. The profile is therefore the one place
 * that holds capability-scoped facts, instead of a second capability-scoped provider class.
 *
 * @param capabilityId        capability this profile executes
 * @param contractVersion     capability contract version this profile implements
 * @param timeoutSeconds      provider execution timeout for this capability
 * @param minimumWidth        lowest accepted output width
 * @param maximumWidth        highest accepted output width
 * @param maximumInputBytes   accepted input size ceiling
 * @param toolchain           executable toolchain label
 * @param inputFormats        accepted subject container formats
 * @param outputFormats       produced image formats
 * @param trustRequirement    trust requirement label
 * @param runtimeRequirement  runtime requirement label
 */
public record FrameExtractCapabilityProfile(
        String capabilityId,
        String contractVersion,
        int timeoutSeconds,
        int minimumWidth,
        int maximumWidth,
        long maximumInputBytes,
        String toolchain,
        java.util.Set<String> inputFormats,
        java.util.Set<String> outputFormats,
        String trustRequirement,
        String runtimeRequirement) {

    public FrameExtractCapabilityProfile {
        require(capabilityId, "capabilityId");
        require(contractVersion, "contractVersion");
        require(toolchain, "toolchain");
        require(trustRequirement, "trustRequirement");
        require(runtimeRequirement, "runtimeRequirement");
        Objects.requireNonNull(inputFormats, "inputFormats");
        Objects.requireNonNull(outputFormats, "outputFormats");
        if (inputFormats.isEmpty()) {
            throw new IllegalArgumentException("inputFormats must not be empty");
        }
        if (outputFormats.isEmpty()) {
            throw new IllegalArgumentException("outputFormats must not be empty");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (minimumWidth <= 0 || maximumWidth < minimumWidth) {
            throw new IllegalArgumentException("invalid width bound " + minimumWidth + ".." + maximumWidth);
        }
        if (maximumInputBytes <= 0) {
            throw new IllegalArgumentException("maximumInputBytes must be positive");
        }
        inputFormats = java.util.Set.copyOf(inputFormats);
        outputFormats = java.util.Set.copyOf(outputFormats);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
