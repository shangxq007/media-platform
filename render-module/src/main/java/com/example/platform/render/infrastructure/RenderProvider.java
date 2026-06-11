package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Set;

public interface RenderProvider {

    RenderResult render(String jobId, String aiScript, String profile);

    List<String> getSupportedProfiles();

    default boolean supports(String capability) {
        return getSupportedProfiles().contains(capability);
    }

    default EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }

    default RenderProviderCapability getCapability() {
        return RenderProviderCapability.legacy(
                "unknown",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                "1920x1080",
                false, false, false,
                Set.of()
        );
    }

    default ProviderStatus getStatus() {
        return ProviderStatus.PRODUCTION;
    }

    default String getPriority() {
        return "P0";
    }

    default ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    default String getPurpose() {
        return "Generic render provider";
    }

    default List<String> getLimitations() {
        return List.of();
    }

    default List<String> getCapabilities() {
        return List.of();
    }

    default boolean isAutoDispatch() {
        return true;
    }

    record EnvironmentValidationResult(boolean valid, String message) {
        public static EnvironmentValidationResult ok() {
            return new EnvironmentValidationResult(true, "OK");
        }
        public static EnvironmentValidationResult failed(String message) {
            return new EnvironmentValidationResult(false, message);
        }
    }

    record RenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution) {}
}
