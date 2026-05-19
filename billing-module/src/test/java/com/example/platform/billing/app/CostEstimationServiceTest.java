package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CostEstimationServiceTest {

    private CostEstimationService service;

    @BeforeEach
    void setUp() {
        service = new CostEstimationService();
    }

    @Test
    void shouldEstimateCostForJavaCv1080p() {
        CostEstimationService.CostEstimate estimate = service.estimate("javacv", "default_1080p", "mp4", 60, false);
        assertNotNull(estimate);
        assertTrue(estimate.estimatedCost() > 0);
        assertEquals("USD", estimate.currency());
        assertEquals("javacv", estimate.providerKey());
        assertEquals("default_1080p", estimate.preset());
        assertFalse(estimate.useGpu());
    }

    @Test
    void shouldEstimateHigherCostFor4k() {
        CostEstimationService.CostEstimate estimate720 = service.estimate("javacv", "default_720p", "mp4", 60, false);
        CostEstimationService.CostEstimate estimate4k = service.estimate("javacv", "4k_2160p", "mp4", 60, false);
        assertTrue(estimate4k.estimatedCost() > estimate720.estimatedCost());
    }

    @Test
    void shouldEstimateHigherCostForGpu() {
        CostEstimationService.CostEstimate cpuEstimate = service.estimate("remote-javacv", "default_1080p", "mp4", 60, false);
        CostEstimationService.CostEstimate gpuEstimate = service.estimate("remote-javacv", "default_1080p", "mp4", 60, true);
        assertTrue(gpuEstimate.estimatedCost() > cpuEstimate.estimatedCost());
    }

    @Test
    void shouldEstimateBestWithinBudget() {
        CostEstimationService.CostEstimate estimate = service.estimateBest("default_1080p", "mp4", 60, false, 1.0);
        assertNotNull(estimate);
        assertTrue(estimate.estimatedCost() <= 1.0);
    }

    @Test
    void shouldFallbackToCheapestWhenBudgetTooLow() {
        CostEstimationService.CostEstimate estimate = service.estimateBest("default_1080p", "mp4", 60, false, 0.001);
        assertNotNull(estimate);
        assertEquals("javacv", estimate.providerKey());
        assertEquals("preview_720p", estimate.preset());
    }

    @Test
    void shouldRegisterCustomProviderProfile() {
        ProviderCostProfile customProfile = new ProviderCostProfile(
                "custom-provider", 0.10, 0.50, 0.03, 0.10, 0.005, "USD",
                Map.of("custom-preset", 2.0));
        service.registerProviderProfile(customProfile);
        CostEstimationService.CostEstimate estimate = service.estimate("custom-provider", "custom-preset", "mp4", 60, false);
        assertNotNull(estimate);
        assertEquals("custom-provider", estimate.providerKey());
    }
}
