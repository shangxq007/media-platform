package com.example.platform.config.firstrender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Incremental optimization of a normal RenderJob; reference is authenticated and must be completed. */
public record IncrementalRenderHttpRequest(@NotBlank String targetRevisionId,
        @NotBlank String referenceRevisionId, @NotBlank String referenceRenderJobId,
        @NotNull @Valid FirstRenderExtentHttpRequest extent,
        @NotNull OutputFormat outputFormat, @NotBlank String profile) {
    FirstRenderPlanHttpRequest target() { return new FirstRenderPlanHttpRequest(targetRevisionId, extent, outputFormat, profile); }
}
