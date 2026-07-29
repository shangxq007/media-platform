package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;

/**
 * Operation to burn subtitles into video frames.
 *
 * <p>Corresponds to {@link ExecutionStepKind#TRANSFORM}.
 */
public record SubtitleBurnInOperation(
        String subtitleInputId,
        String fontName,
        int fontSize,
        String position,
        String color
) implements Serializable, MediaOperation {

    public SubtitleBurnInOperation {
        Objects.requireNonNull(subtitleInputId, "subtitleInputId");
        if (subtitleInputId.isBlank()) throw new IllegalArgumentException("subtitleInputId must not be blank");
    }

    /**
     * Creates a subtitle burn-in operation with default styling.
     */
    public static SubtitleBurnInOperation of(String subtitleInputId) {
        return new SubtitleBurnInOperation(subtitleInputId, "Arial", 24, "bottom", "white");
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.TRANSFORM;
    }

    @Override
    public String operationType() {
        return "SUBTITLE_BURN_IN";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "subtitleBurnIn{" +
                "input=" + subtitleInputId +
                ",font=" + (fontName != null ? fontName : "") +
                ",size=" + fontSize +
                ",pos=" + (position != null ? position : "") +
                ",color=" + (color != null ? color : "") +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
