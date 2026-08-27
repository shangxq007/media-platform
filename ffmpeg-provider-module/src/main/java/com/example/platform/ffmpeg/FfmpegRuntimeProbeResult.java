package com.example.platform.ffmpeg;

import com.example.platform.workerfabric.domain.ProviderProbeResult;
import java.util.Objects;
import java.util.Optional;

/** Exact bounded FFmpeg build/version evidence; never eligibility or capacity authority. */
public record FfmpegRuntimeProbeResult(
        ProviderProbeResult providerProbeResult,
        Optional<String> runtimeVersion,
        Optional<String> exactVersionLine,
        Optional<String> exactBuildConfigurationLine) {

    public FfmpegRuntimeProbeResult {
        Objects.requireNonNull(providerProbeResult, "providerProbeResult");
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        exactVersionLine = Objects.requireNonNull(exactVersionLine, "exactVersionLine");
        exactBuildConfigurationLine = Objects.requireNonNull(
                exactBuildConfigurationLine, "exactBuildConfigurationLine");
        boolean healthy = providerProbeResult.status() == ProviderProbeResult.Status.HEALTHY;
        if (healthy != (runtimeVersion.isPresent()
                && exactVersionLine.isPresent()
                && exactBuildConfigurationLine.isPresent())) {
            throw new IllegalArgumentException("healthy probe requires exact version and build evidence");
        }
    }
}
