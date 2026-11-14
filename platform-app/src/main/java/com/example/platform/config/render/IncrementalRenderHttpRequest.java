package com.example.platform.config.render;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Incremental optimization of a normal RenderJob; reference is authenticated and must be completed. */
public record IncrementalRenderHttpRequest(@NotBlank String targetRevisionId,
        @NotBlank String referenceRevisionId, @NotBlank String referenceRenderJobId,
        @NotNull @Valid RenderJobExtentHttpRequest extent,
        @NotNull OutputFormat outputFormat, @NotBlank String profile) {
    RenderJobSubmissionHttpRequest target() { return new RenderJobSubmissionHttpRequest(targetRevisionId, extent, outputFormat, profile); }
}
