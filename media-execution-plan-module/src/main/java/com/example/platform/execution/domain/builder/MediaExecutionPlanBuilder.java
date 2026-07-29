package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaOperation;

import java.util.*;

/**
 * Pure function builder for MediaExecutionPlan.
 *
 * <p>Insertion-order independent: building the same semantic plan with different
 * insertion orders produces identical plans. Full preflight validation — no
 * partial plans on error. Caller input lists are never mutated.
 */
public final class MediaExecutionPlanBuilder {

    private ExecutionPlanId planId;
    private String tenantId;
    private String productId;
    private String timelineRevisionId;
    private String timelineRevisionDigest;
    private ExecutionPlanSchemaVersion schemaVersion;
    private final List<ExecutionInputBinding> inputs = new ArrayList<>();
    private final List<MediaExecutionStep> steps = new ArrayList<>();
    private final List<ExecutionDependency> edges = new ArrayList<>();
    private final List<ExecutionOutputDeclaration> outputs = new ArrayList<>();
    private ExecutionCreationContext creationContext;

    private MediaExecutionPlanBuilder() {
    }

    /**
     * Creates a new builder.
     */
    public static MediaExecutionPlanBuilder create() {
        return new MediaExecutionPlanBuilder();
    }

    public MediaExecutionPlanBuilder planId(ExecutionPlanId planId) {
        this.planId = Objects.requireNonNull(planId, "planId");
        return this;
    }

    public MediaExecutionPlanBuilder tenantId(String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        return this;
    }

    public MediaExecutionPlanBuilder productId(String productId) {
        this.productId = Objects.requireNonNull(productId, "productId");
        return this;
    }

    public MediaExecutionPlanBuilder timelineRevisionId(String timelineRevisionId) {
        this.timelineRevisionId = Objects.requireNonNull(timelineRevisionId, "timelineRevisionId");
        return this;
    }

    public MediaExecutionPlanBuilder timelineRevisionDigest(String timelineRevisionDigest) {
        this.timelineRevisionDigest = Objects.requireNonNull(timelineRevisionDigest, "timelineRevisionDigest");
        return this;
    }

    public MediaExecutionPlanBuilder schemaVersion(ExecutionPlanSchemaVersion schemaVersion) {
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        return this;
    }

    public MediaExecutionPlanBuilder creationContext(ExecutionCreationContext creationContext) {
        this.creationContext = Objects.requireNonNull(creationContext, "creationContext");
        return this;
    }

    /**
     * Adds an input binding. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addInput(ExecutionInputBinding input) {
        Objects.requireNonNull(input, "input");
        this.inputs.add(input);
        return this;
    }

    /**
     * Adds multiple input bindings. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addInputs(Collection<ExecutionInputBinding> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        for (ExecutionInputBinding input : inputs) {
            addInput(input);
        }
        return this;
    }

    /**
     * Adds a step. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addStep(MediaExecutionStep step) {
        Objects.requireNonNull(step, "step");
        this.steps.add(step);
        return this;
    }

    /**
     * Adds multiple steps. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addSteps(Collection<MediaExecutionStep> steps) {
        Objects.requireNonNull(steps, "steps");
        for (MediaExecutionStep step : steps) {
            addStep(step);
        }
        return this;
    }

    /**
     * Adds a dependency edge. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addEdge(ExecutionDependency edge) {
        Objects.requireNonNull(edge, "edge");
        this.edges.add(edge);
        return this;
    }

    /**
     * Adds multiple dependency edges. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addEdges(Collection<ExecutionDependency> edges) {
        Objects.requireNonNull(edges, "edges");
        for (ExecutionDependency edge : edges) {
            addEdge(edge);
        }
        return this;
    }

    /**
     * Adds an output declaration. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addOutput(ExecutionOutputDeclaration output) {
        Objects.requireNonNull(output, "output");
        this.outputs.add(output);
        return this;
    }

    /**
     * Adds multiple output declarations. Caller's list is not mutated.
     */
    public MediaExecutionPlanBuilder addOutputs(Collection<ExecutionOutputDeclaration> outputs) {
        Objects.requireNonNull(outputs, "outputs");
        for (ExecutionOutputDeclaration output : outputs) {
            addOutput(output);
        }
        return this;
    }

    /**
     * Builds the execution plan with full validation.
     *
     * @return a validated, immutable MediaExecutionPlan
     * @throws ExecutionPlanDomainException if validation fails
     */
    public MediaExecutionPlan build() {
        // Validate required fields
        Objects.requireNonNull(planId, "planId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(productId, "productId required");
        Objects.requireNonNull(timelineRevisionId, "timelineRevisionId required");
        Objects.requireNonNull(timelineRevisionDigest, "timelineRevisionDigest required");
        Objects.requireNonNull(schemaVersion, "schemaVersion required");
        Objects.requireNonNull(creationContext, "creationContext required");

        // Use defensive copies — caller input immutability
        List<ExecutionInputBinding> inputsCopy = List.copyOf(inputs);
        List<MediaExecutionStep> stepsCopy = List.copyOf(steps);
        List<ExecutionDependency> edgesCopy = List.copyOf(edges);
        List<ExecutionOutputDeclaration> outputsCopy = List.copyOf(outputs);

        // Compute digest before validation (digest is part of identity)
        String digestInput = buildDigestInput(planId, tenantId, productId,
                timelineRevisionId, timelineRevisionDigest, schemaVersion,
                inputsCopy, stepsCopy, edgesCopy, outputsCopy);
        ExecutionPlanDigest digest = new ExecutionPlanDigest(
                ExecutionPlanCanonicalSerializer.sha256Hex(digestInput));

        MediaExecutionPlan plan = new MediaExecutionPlan(
                planId, tenantId, productId,
                timelineRevisionId, timelineRevisionDigest,
                schemaVersion, inputsCopy, stepsCopy, edgesCopy, outputsCopy,
                creationContext, digest);

        // Full preflight validation
        MediaExecutionPlanValidator.validate(plan);

        return plan;
    }

    /**
     * Builds the digest input string for the plan.
     */
    private String buildDigestInput(
            ExecutionPlanId planId,
            String tenantId,
            String productId,
            String timelineRevisionId,
            String timelineRevisionDigest,
            ExecutionPlanSchemaVersion schemaVersion,
            List<ExecutionInputBinding> inputs,
            List<MediaExecutionStep> steps,
            List<ExecutionDependency> edges,
            List<ExecutionOutputDeclaration> outputs) {
        StringBuilder sb = new StringBuilder("planDigest{");
        sb.append("schema=").append(schemaVersion.value());
        sb.append(",rev=").append(timelineRevisionId);
        sb.append(",revDigest=").append(timelineRevisionDigest);

        sb.append(",inputs=[");
        inputs.stream()
                .map(ExecutionInputBinding::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append(",steps=[");
        steps.stream()
                .map(MediaExecutionStep::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append(",edges=[");
        edges.stream()
                .map(ExecutionDependency::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append(",outputs=[");
        outputs.stream()
                .map(ExecutionOutputDeclaration::canonicalForm)
                .sorted()
                .forEach(s -> sb.append(s).append(';'));
        sb.append(']');

        sb.append('}');
        return sb.toString();
    }
}
