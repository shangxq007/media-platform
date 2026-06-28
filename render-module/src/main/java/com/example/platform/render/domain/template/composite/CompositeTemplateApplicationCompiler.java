package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composite template application compiler — dry-run expansion only.
 *
 * <p>Internal domain compiler. Produces provider-neutral expansion plan.
 * Does not call child compilers, render pipeline, StorageRuntime, or ProductRuntime.</p>
 */
public class CompositeTemplateApplicationCompiler {

    /**
     * Expand a composite template into child application requests.
     */
    public CompositeTemplateExpansionPlan expand(
            CompositeTemplateDefinition definition,
            TemplateApplicationRequest parentRequest) {

        if (definition == null) {
            return failedPlan(null, "Definition must not be null");
        }
        if (parentRequest == null) {
            return failedPlan(definition.id(), "Parent request must not be null");
        }

        // Validate required target bindings can be resolved
        List<CompositeTemplateValidationError> errors = new ArrayList<>();

        // Check required target bindings have matching parent targets
        for (TemplateTargetBinding binding : definition.targetBindings()) {
            if (binding.required()) {
                boolean hasParentTarget = parentRequest.targets().stream()
                        .anyMatch(t -> t.role() == binding.parentRole());
                if (!hasParentTarget) {
                    errors.add(new CompositeTemplateValidationError(
                            "targetBindings[" + binding.parentRole() + "]",
                            "MISSING_PARENT_TARGET",
                            "Parent target " + binding.parentRole() + " is required but not provided"));
                }
            }
        }

        if (!errors.isEmpty()) {
            return new CompositeTemplateExpansionPlan(
                    new CompositeTemplateExpansionPlanId("plan-" + definition.id().value()),
                    definition.id(), List.of(),
                    CompositeTemplateValidationResult.failure(errors),
                    Map.of());
        }

        // Expand each child into a step
        List<CompositeTemplateExpansionStep> steps = new ArrayList<>();
        for (CompositeTemplateChild child : definition.children()) {
            // Build child targets from bindings
            List<TemplateTarget> childTargets = new ArrayList<>();
            for (TemplateTargetBinding binding : definition.targetBindings()) {
                if (binding.childId().equals(child.id())) {
                    // Find matching parent target
                    parentRequest.targets().stream()
                            .filter(t -> t.role() == binding.parentRole())
                            .findFirst()
                            .ifPresent(parentTarget -> childTargets.add(
                                    new TemplateTarget(binding.childRole(),
                                            parentTarget.targetType(),
                                            parentTarget.targetId(),
                                            parentTarget.safeMetadata())));
                }
            }

            // Build child parameters from bindings
            List<TemplateParameter> childParams = new ArrayList<>();
            for (TemplateParameterBinding paramBinding : definition.parameterBindings()) {
                if (paramBinding.childId().equals(child.id())) {
                    // Find matching parent parameter
                    parentRequest.parameters().stream()
                            .filter(p -> paramBinding.parentParameterName().equals(p.name()))
                            .findFirst()
                            .ifPresent(parentParam -> childParams.add(
                                    new TemplateParameter(
                                            paramBinding.childParameterName(),
                                            paramBinding.childParameterName(),
                                            parentParam.type(),
                                            paramBinding.required(),
                                            parentParam.defaultValue())));
                }
            }

            // Create child request
            TemplateApplicationRequest childRequest = new TemplateApplicationRequest(
                    parentRequest.projectId(),
                    child.childTemplateId(),
                    child.childTemplateVersion(),
                    childTargets,
                    childParams,
                    parentRequest.safeMetadata());

            steps.add(new CompositeTemplateExpansionStep(
                    child.id(),
                    child.childTemplateId(),
                    childRequest,
                    CompositeTemplateExpansionStepStatus.READY,
                    Map.of()));
        }

        return new CompositeTemplateExpansionPlan(
                new CompositeTemplateExpansionPlanId("plan-" + definition.id().value()),
                definition.id(),
                steps,
                CompositeTemplateValidationResult.success(),
                Map.of());
    }

    private CompositeTemplateExpansionPlan failedPlan(
            CompositeTemplateDefinitionId compositeId, String message) {
        return new CompositeTemplateExpansionPlan(
                new CompositeTemplateExpansionPlanId("plan-failed"),
                compositeId != null ? compositeId : new CompositeTemplateDefinitionId("unknown"),
                List.of(),
                CompositeTemplateValidationResult.failure(
                        List.of(new CompositeTemplateValidationError("_", "FAILED", message))),
                Map.of());
    }
}
