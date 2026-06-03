package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Loads and parses SpatialPlan from JSON files.
 */
public class SpatialPlanLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Load a SpatialPlan from a JSON file path.
     */
    public static Optional<SpatialPlan> loadFromFile(Path planPath) {
        try {
            if (!Files.isRegularFile(planPath)) {
                return Optional.empty();
            }
            String json = Files.readString(planPath);
            return parse(json);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Load a SpatialPlan from classpath resource.
     */
    public static Optional<SpatialPlan> loadFromResource(String resourcePath) {
        try (InputStream is = SpatialPlanLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return Optional.empty();
            }
            return parse(new String(is.readAllBytes()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Parse a SpatialPlan from JSON string.
     */
    public static Optional<SpatialPlan> parse(String json) {
        try {
            return Optional.ofNullable(MAPPER.readValue(json, SpatialPlan.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get supported operations from a SpatialPlan.
     */
    public static List<SpatialOperation> getSupportedOperations(SpatialPlan plan) {
        if (plan == null || plan.operations() == null) {
            return List.of();
        }
        return plan.operations().stream()
                .filter(SpatialOperation::isSupported)
                .toList();
    }

    /**
     * Get crop operations from a SpatialPlan.
     */
    public static List<SpatialOperation> getCropOperations(SpatialPlan plan) {
        if (plan == null || plan.operations() == null) {
            return List.of();
        }
        return plan.operations().stream()
                .filter(SpatialOperation::isCrop)
                .filter(SpatialOperation::isSupported)
                .toList();
    }

    /**
     * Get overlay/composite operations from a SpatialPlan.
     */
    public static List<SpatialOperation> getOverlayOperations(SpatialPlan plan) {
        if (plan == null || plan.operations() == null) {
            return List.of();
        }
        return plan.operations().stream()
                .filter(SpatialOperation::isOverlay)
                .filter(SpatialOperation::isSupported)
                .toList();
    }
}
