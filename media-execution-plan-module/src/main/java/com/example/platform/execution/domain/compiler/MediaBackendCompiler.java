package com.example.platform.execution.domain;

/**
 * FROZEN interface for backend compilers.
 *
 * <p>Translates a MediaExecutionPlan into a backend-specific execution manifest.
 * The manifest contains backend-specific commands, resource bindings, and scheduling hints.
 *
 * <p>This interface is FROZEN — do not implement. Backend compilers (FFmpeg, Remotion,
 * GPAC, Blender) are out of scope for V1.
 */
public sealed interface MediaBackendCompiler permits
        MediaBackendCompiler.Stub {

    /**
     * Compiles the plan into a backend-specific manifest.
     *
     * @param plan the execution plan to compile
     * @return the backend-specific manifest
     * @throws UnsupportedOperationException always — not implemented
     */
    ExecutionProvider.ExecutionManifest compile(MediaExecutionPlan plan);

    /**
     * Stub implementation that always throws UnsupportedOperationException.
     */
    record Stub() implements MediaBackendCompiler {
        @Override
        public ExecutionProvider.ExecutionManifest compile(MediaExecutionPlan plan) {
            throw new UnsupportedOperationException(
                    "MediaBackendCompiler is not yet implemented");
        }
    }
}
