package com.example.platform.extension.domain;

public record ExtensionResourceLimits(
        int maxConcurrency,
        int maxMemoryMb,
        int maxCpuPercent,
        int maxQueueSize,
        long maxInputBytes,
        long maxOutputBytes,
        long timeoutMs
) {
    public static final ExtensionResourceLimits DEFAULTS = new ExtensionResourceLimits(
            4, 256, 50, 100, 10 * 1024 * 1024, 4 * 1024 * 1024, 30_000
    );

    public static final ExtensionResourceLimits UNTRUSTED = new ExtensionResourceLimits(
            1, 64, 25, 10, 1024 * 1024, 512 * 1024, 10_000
    );

    public static final ExtensionResourceLimits FULLY_TRUSTED = new ExtensionResourceLimits(
            16, 1024, 100, 500, 100 * 1024 * 1024, 64 * 1024 * 1024, 120_000
    );

    public static ExtensionResourceLimits forTrustLevel(ExtensionTrustLevel level) {
        return switch (level) {
            case FULLY_TRUSTED -> FULLY_TRUSTED;
            case SEMI_TRUSTED -> DEFAULTS;
            case UNTRUSTED -> UNTRUSTED;
        };
    }

    public ExtensionResourceLimits overrideWith(ExtensionResourceLimits override) {
        if (override == null) return this;
        return new ExtensionResourceLimits(
                override.maxConcurrency > 0 ? override.maxConcurrency : this.maxConcurrency,
                override.maxMemoryMb > 0 ? override.maxMemoryMb : this.maxMemoryMb,
                override.maxCpuPercent > 0 ? override.maxCpuPercent : this.maxCpuPercent,
                override.maxQueueSize > 0 ? override.maxQueueSize : this.maxQueueSize,
                override.maxInputBytes > 0 ? override.maxInputBytes : this.maxInputBytes,
                override.maxOutputBytes > 0 ? override.maxOutputBytes : this.maxOutputBytes,
                override.timeoutMs > 0 ? override.timeoutMs : this.timeoutMs
        );
    }
}
