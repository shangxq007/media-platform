package com.example.platform.render.domain.scenario;

import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.editing.TimelineEditOperation;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of an internal scenario.
 * Immutable record. Internal domain model.
 * Contains the scenario identity, category, input timeline or edit operations,
 * expected outcome, and safety expectations.
 */
public record InternalScenarioDefinition(
        InternalScenarioId id,
        InternalScenarioName name,
        InternalScenarioCategory category,
        String description,
        InternalScenarioStatus status,
        TimelineSpec inputTimeline,
        List<TimelineEditOperation> editOperations,
        InternalScenarioExpectedOutcome expectedOutcome,
        Map<String, String> safetyExpectations,
        Map<String, String> safeMetadata) {

    public InternalScenarioDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        status = status == null ? InternalScenarioStatus.ACTIVE : status;
        editOperations = editOperations == null ? List.of() : List.copyOf(editOperations);
        Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        safetyExpectations = safetyExpectations == null ? Map.of() : Map.copyOf(safetyExpectations);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
