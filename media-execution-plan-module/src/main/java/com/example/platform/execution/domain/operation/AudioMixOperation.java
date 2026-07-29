package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Operation to mix multiple audio sources.
 *
 * <p>Corresponds to {@link ExecutionStepKind#TRANSFORM}.
 */
public record AudioMixOperation(
        List<String> inputChannelLayouts,
        String outputChannelLayout,
        String mixingMode,
        float masterGainDb
) implements Serializable, MediaOperation {

    public AudioMixOperation {
        Objects.requireNonNull(inputChannelLayouts, "inputChannelLayouts");
        if (inputChannelLayouts.isEmpty()) throw new IllegalArgumentException("inputChannelLayouts must not be empty");
        inputChannelLayouts = List.copyOf(inputChannelLayouts);
        Objects.requireNonNull(outputChannelLayout, "outputChannelLayout");
        Objects.requireNonNull(mixingMode, "mixingMode");
    }

    /**
     * Creates a stereo mix-down operation.
     */
    public static AudioMixOperation stereoMixDown(List<String> inputs) {
        return new AudioMixOperation(inputs, "stereo", "mix", 0.0f);
    }

    /**
     * Creates a passthrough operation for single input.
     */
    public static AudioMixOperation passthrough(String channelLayout) {
        return new AudioMixOperation(List.of(channelLayout), channelLayout, "passthrough", 0.0f);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.TRANSFORM;
    }

    @Override
    public String operationType() {
        return "AUDIO_MIX";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "audioMix{" +
                "inputs=" + inputChannelLayouts +
                ",output=" + outputChannelLayout +
                ",mode=" + mixingMode +
                ",gain=" + masterGainDb +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
