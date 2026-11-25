package com.example.platform.web.render;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.application.TimelineOperationWireContract;
import com.example.platform.operation.application.TimelineOperationWireContract.Description;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/semantic/operations")
public final class SemanticDefinitionController {
    private final TimelineProjectAuthorizationService authorization;

    public SemanticDefinitionController(TimelineProjectAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @GetMapping
    public List<Description> definitions(@PathVariable String tenantId, @PathVariable String projectId) {
        authorization.requireRead(tenantId, projectId);
        return OperationDefinition.V3.ALL.stream().map(TimelineOperationWireContract::describe).toList();
    }

    /** Built-in primitives supported for authoring. Execution depends on the pinned revision and extent. */
    public record CreativeDefinition(String kind, String definitionId, String format, String operationId,
            Map<String, TimelineOperationWireContract.Field> parameters, String executionCheck) {}

    @GetMapping("/creative-definitions")
    public List<CreativeDefinition> creativeDefinitions(@PathVariable String tenantId, @PathVariable String projectId) {
        authorization.requireRead(tenantId, projectId);
        return List.of(creative("EFFECT", com.example.platform.timeline.semantics.effect.BasicVideoEffect.EFFECT_KEY,
                        com.example.platform.timeline.semantics.effect.BasicVideoEffect.FORMAT, OperationDefinition.V3.SET_VIDEO_EFFECT),
                creative("TRANSITION", com.example.platform.timeline.canonicalmodel.CanonicalTransition.CROSS_DISSOLVE,
                        com.example.platform.timeline.canonicalmodel.CanonicalTransition.CROSS_DISSOLVE_VERSION, OperationDefinition.V3.CREATE_CROSS_DISSOLVE));
    }

    private static CreativeDefinition creative(String kind, String id, String format, OperationDefinition operation) {
        var wire = TimelineOperationWireContract.describe(operation);
        return new CreativeDefinition(kind, id, format, wire.id(), wire.parameters(), "REVISION_FEASIBILITY");
    }

    @GetMapping("/batch-schema")
    public java.util.Map<String, Object> batchSchema(@PathVariable String tenantId, @PathVariable String projectId) {
        authorization.requireRead(tenantId, projectId);
        return TimelineBatchSchema.batch();
    }

    @GetMapping("/{definitionId}/{version}")
    public Description definition(@PathVariable String tenantId, @PathVariable String projectId,
            @PathVariable String definitionId, @PathVariable String version) {
        authorization.requireRead(tenantId, projectId);
        return OperationDefinition.V3.ALL.stream()
                .filter(d -> d.definitionId().value().equals(definitionId) && d.version().toString().equals(version))
                .map(TimelineOperationWireContract::describe).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Definition unavailable"));
    }
}
