package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * SPI for render provider implementations.
 *
 * <p>Render providers are responsible for executing render jobs using specific
 * backends (e.g., FFmpeg, MLT, Mock). Implementations must be thread-safe and
 * stateless.</p>
 *
 * <h3>Capability-Based Routing</h3>
 * <p>The {@link #supports(String)} method enables the {@link RenderProviderRouter}
 * to select the appropriate provider based on render capabilities rather than
 * just profile strings.</p>
 *
 * <h3>Environment Validation</h3>
 * <p>The {@link #validateEnvironment()} method is called at startup to verify
 * that the provider's dependencies (binaries, libraries, configuration) are
 * available. Providers that fail validation are excluded from routing.</p>
 *
 * @see RenderProviderRouter
 * @see MockRenderProvider
 */
public interface RenderProvider {

    /**
     * Execute a render job.
     *
     * @param jobId     the unique render job identifier
     * @param aiScript  the AI-generated script/instructions for the render
     * @param profile   the render profile (e.g., "social_1080p", "default_720p")
     * @return the render result containing artifact metadata
     * @throws IllegalStateException if the render fails
     */
    RenderResult render(String jobId, String aiScript, String profile);

    /**
     * Returns the list of render profiles this provider supports.
     *
     * @return list of profile identifiers (e.g., "social_1080p")
     */
    List<String> getSupportedProfiles();

    /**
     * Checks whether this provider supports the given capability.
     *
     * <p>Capabilities are used for fine-grained routing beyond simple profile
     * matching. Examples: "h264", "h265", "4k", "watermark", "subtitle-burn".</p>
     *
     * @param capability the capability identifier to check
     * @return {@code true} if this provider supports the capability
     */
    default boolean supports(String capability) {
        return getSupportedProfiles().contains(capability);
    }

    /**
     * Validates that the provider's execution environment is correctly configured.
     *
     * <p>Called at application startup. Implementations should check for:</p>
     * <ul>
     *   <li>Required binaries (e.g., ffmpeg, melt) are on the PATH</li>
     *   <li>Required libraries/plugins are available</li>
     *   <li>Configuration files are present and valid</li>
     *   <li>Temp directories are writable</li>
     * </ul>
     *
     * @return a validation result indicating success or failure with details
     */
    default EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }

    /**
     * Result of environment validation.
     *
     * @param valid   whether the environment is correctly configured
     * @param message human-readable status message
     */
    record EnvironmentValidationResult(boolean valid, String message) {
        public static EnvironmentValidationResult ok() {
            return new EnvironmentValidationResult(true, "OK");
        }

        public static EnvironmentValidationResult failed(String message) {
            return new EnvironmentValidationResult(false, message);
        }
    }

    /**
     * Result of a render operation.
     *
     * @param artifactId the unique artifact identifier
     * @param storageUri URI where the rendered content is stored
     * @param duration   render duration in seconds
     * @param format     output format (e.g., "mp4")
     * @param resolution output resolution (e.g., "1920x1080")
     */
    record RenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution) {}
}
