package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import java.util.Objects;

/** Typed, derived CPU H.264/yuv420p fragmented-MP4 stdout plan for one exact task. */
public record FfmpegCpuTranscodePlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        ExecutionInputId inputId,
        ExecutionOutputId outputId,
        VideoCodec videoCodec,
        PixelFormat pixelFormat,
        ContainerFormat containerFormat) implements ProviderNativeExecutionPlan {

    public FfmpegCpuTranscodePlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(videoCodec, "videoCodec");
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        Objects.requireNonNull(containerFormat, "containerFormat");
        if (!providerBindingPin.equals(FfmpegCpuProvider.BINDING)
                || videoCodec != VideoCodec.H264_LIBX264
                || pixelFormat != PixelFormat.YUV420P
                || containerFormat != ContainerFormat.FRAGMENTED_MP4_STDOUT) {
            throw new IllegalArgumentException("FFmpeg CPU plan is outside the bounded transcode slice");
        }
    }

    public enum VideoCodec { H264_LIBX264 }
    public enum PixelFormat { YUV420P }
    public enum ContainerFormat { FRAGMENTED_MP4_STDOUT }
}
