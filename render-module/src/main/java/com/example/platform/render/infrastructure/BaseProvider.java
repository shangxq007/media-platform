package com.example.platform.render.infrastructure;

import java.util.List;

public interface BaseProvider {

    ProviderMetadata getMetadata();

    default EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }

    default JobValidationResult validateJob(ProviderJob job) {
        return JobValidationResult.validResult();
    }

    default Estimate estimate(ProviderJob job) {
        return null;
    }

    record EnvironmentValidationResult(boolean valid, String message) {
        public static EnvironmentValidationResult ok() {
            return new EnvironmentValidationResult(true, "OK");
        }
        public static EnvironmentValidationResult failed(String message) {
            return new EnvironmentValidationResult(false, message);
        }
    }

    record JobValidationResult(boolean valid, String reason) {
        public static JobValidationResult validResult() {
            return new JobValidationResult(true, "OK");
        }
        public static JobValidationResult invalid(String reason) {
            return new JobValidationResult(false, reason);
        }
    }

    record Estimate(double estimatedCost, long estimatedDurationMs, String currency) {
        public static Estimate of(double cost, long durationMs) {
            return new Estimate(cost, durationMs, "USD");
        }
    }
}
