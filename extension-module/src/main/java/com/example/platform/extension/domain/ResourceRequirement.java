package com.example.platform.extension.domain;

/**
 * Resource requirement declaration (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>EXTENDS the existing {@link ExtensionResourceLimits} concept with the
 * frozen additional concerns: GPU, temporary disk, network and execution
 * duration. P1 behavior is declaration + validation only — no distributed
 * scheduling and no hard resource enforcement (except where an existing check
 * is reused exactly).</p>
 *
 * @param maxConcurrency       required (FFmpeg self-description: 1)
 * @param maxMemoryMb          declared bound
 * @param maxCpuPercent        declared bound
 * @param maxQueueSize         required (FFmpeg: 0)
 * @param maxInputBytes        declared bound
 * @param maxOutputBytes       declared bound
 * @param timeoutMs            declared bound (FFmpeg: 60000 — ToolSandboxPolicy default)
 * @param gpu                  required (FFmpeg local: false)
 * @param temporaryDiskMb      declared temporary-disk bound
 * @param networkAllowed       declared network flag (FFmpeg: false — ToolSandboxPolicy default)
 * @param executionDurationMs  declared execution duration (&lt;= timeoutMs)
 */
public record ResourceRequirement(
        int maxConcurrency,
        int maxMemoryMb,
        int maxCpuPercent,
        int maxQueueSize,
        long maxInputBytes,
        long maxOutputBytes,
        long timeoutMs,
        boolean gpu,
        int temporaryDiskMb,
        boolean networkAllowed,
        long executionDurationMs) {

    /** Platform maximum timeout accepted by P1 validation (120s). */
    public static final long PLATFORM_MAX_TIMEOUT_MS = 120_000L;

    /** Convenience factory seeding from the existing ExtensionResourceLimits concept. */
    public static ResourceRequirement fromExtensionResourceLimits(
            ExtensionResourceLimits seed,
            boolean gpu,
            int temporaryDiskMb,
            boolean networkAllowed,
            long executionDurationMs) {
        return new ResourceRequirement(
                seed.maxConcurrency(),
                seed.maxMemoryMb(),
                seed.maxCpuPercent(),
                seed.maxQueueSize(),
                seed.maxInputBytes(),
                seed.maxOutputBytes(),
                seed.timeoutMs(),
                gpu,
                temporaryDiskMb,
                networkAllowed,
                executionDurationMs);
    }

    /** Frozen FFmpeg self-description resource declaration. */
    public static ResourceRequirement ffmpegDefaults() {
        return new ResourceRequirement(
                1, 256, 50, 0, 64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L,
                false, 4096, false, 60_000L);
    }

    /**
     * Numeric bound validation (frozen A045): all numeric bounds &gt;= 0;
     * timeout within platform max (120s); execution duration &lt;= timeout.
     */
    public boolean boundsValid() {
        if (maxConcurrency < 0 || maxMemoryMb < 0 || maxCpuPercent < 0 || maxQueueSize < 0) {
            return false;
        }
        if (maxInputBytes < 0 || maxOutputBytes < 0) {
            return false;
        }
        if (timeoutMs <= 0 || timeoutMs > PLATFORM_MAX_TIMEOUT_MS) {
            return false;
        }
        if (temporaryDiskMb < 0) {
            return false;
        }
        return executionDurationMs >= 0 && executionDurationMs <= timeoutMs;
    }
}
