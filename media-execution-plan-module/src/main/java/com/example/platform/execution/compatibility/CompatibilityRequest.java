package com.example.platform.execution.compatibility;

import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One immutable PhysicalPlanUnit plus additional frozen Stage-1 requirements. */
public record CompatibilityRequest(
        PhysicalPlanUnit physicalPlanUnit,
        List<StaticCompatibilityConstraint> additionalConstraints) {

    public CompatibilityRequest {
        Objects.requireNonNull(physicalPlanUnit, "physicalPlanUnit");
        Objects.requireNonNull(additionalConstraints, "additionalConstraints");
        var canonical = new ArrayList<StaticCompatibilityConstraint>(additionalConstraints.size());
        for (StaticCompatibilityConstraint constraint : additionalConstraints) {
            canonical.add(Objects.requireNonNull(constraint, "additionalConstraints element"));
        }
        canonical.sort(Comparator.comparing(StaticCompatibilityConstraint::canonicalKey));
        for (int i = 1; i < canonical.size(); i++) {
            if (canonical.get(i - 1).canonicalKey().equals(canonical.get(i).canonicalKey())) {
                throw new IllegalArgumentException("duplicate static compatibility constraint");
            }
        }
        additionalConstraints = List.copyOf(canonical);
    }

    public static CompatibilityRequest forUnit(PhysicalPlanUnit physicalPlanUnit) {
        return new CompatibilityRequest(physicalPlanUnit, List.of());
    }
}
