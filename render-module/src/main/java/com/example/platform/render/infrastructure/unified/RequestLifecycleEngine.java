package com.example.platform.render.infrastructure.unified;

import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine;
import com.example.platform.shared.commercial.CommercialDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Request Lifecycle Engine - the NEW orchestrator for render job execution.
 * 
 * <p>This engine creates and manages the UnifiedRequestGraph for each render job,
 * passing graph context to all subsystems and attaching all results into the graph.
 * 
 * <p>The entire render job lifecycle can be reconstructed from the graph.
 */
@Service
public class RequestLifecycleEngine {

    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleEngine.class);

    private final UnifiedExecutionTracer tracer;
    private final UnifiedGraphRepository graphRepository;
    private final RenderJobStateMachine stateMachine;
    private final ProviderRuntimeEngine providerRuntimeEngine;

    public RequestLifecycleEngine(
            UnifiedExecutionTracer tracer,
            UnifiedGraphRepository graphRepository,
            RenderJobStateMachine stateMachine,
            ProviderRuntimeEngine providerRuntimeEngine) {
        this.tracer = tracer;
        this.graphRepository = graphRepository;
        this.stateMachine = stateMachine;
        this.providerRuntimeEngine = providerRuntimeEngine;
    }

    /**
     * Start a new request lifecycle.
     * Creates the UnifiedRequestGraph and initializes tracing.
     */
    public UnifiedRequestGraph startRequest(String tenantId, String workspaceId) {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);

        UnifiedRequestGraph graph = tracer.createGraph(requestId, tenantId, workspaceId);

        // Emit initial execution state
        tracer.traceExecutionState(requestId, "INIT", "QUEUED",
                "Request created", "RequestLifecycleEngine");

        log.info("Started request lifecycle: {}", requestId);
        return graph;
    }

    /**
     * Record a precomputed neutral commercial decision without recomputing H5 authority.
     */
    public CommercialDecision executeCommercialPhase(String requestId, CommercialDecision decision) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) {
            throw new IllegalStateException("No active graph for request: " + requestId);
        }

        // Emit execution state transition
        tracer.traceExecutionState(requestId, "QUEUED", "SELECTING_PROVIDER",
                "Recording commercial admission", "RequestLifecycleEngine");

        tracer.traceCommercialDecision(
                requestId,
                decision.allowed() ? "ALLOWED" : "DENIED",
                decision.reason().name(),
                decision.authorityVersion()
        );
        log.info("[{}] Commercial decision: {}", requestId, decision.reason());
        return decision;
    }

    /**
     * Execute the provider selection phase.
     * Returns the provider decision and updates the graph.
     */
    public ProviderRuntimeEngine.ProviderResolutionResult executeProviderPhase(
            String requestId, ProviderRuntimeEngine.ProviderResolutionRequest request) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) {
            throw new IllegalStateException("No active graph for request: " + requestId);
        }

        // Emit execution state transition
        tracer.traceExecutionState(requestId, "SELECTING_PROVIDER", "PROVIDER_SELECTED",
                "Starting provider selection", "RequestLifecycleEngine");

        // Execute provider selection
        ProviderRuntimeEngine.ProviderResolutionResult result = providerRuntimeEngine.resolveProvider(request);

        // Emit provider decision node
        boolean fallbackTriggered = result.executionResult() != null && result.executionResult().usedFallback();
        tracer.traceProviderDecision(
                requestId,
                result.selectedProviderName(),
                result.selectedProviderName(),
                result.negotiationResult() != null ? result.negotiationResult().selectionReason() : "selected",
                fallbackTriggered
        );

        // Set job ID on graph
        if (result.isSuccess()) {
            tracer.setJobId(requestId, request.jobId());
        }

        log.info("[{}] Provider selected: {}", requestId, result.selectedProviderName());
        return result;
    }

    /**
     * Execute the render phase.
     * Updates the graph with execution state.
     */
    public void executeRenderPhase(String requestId, String jobId) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) return;

        // Emit execution state transition
        tracer.traceExecutionState(requestId, "PROVIDER_SELECTED", "EXECUTING",
                "Starting render execution", "RequestLifecycleEngine");

        tracer.setJobId(requestId, jobId);
    }

    /**
     * Complete the render phase with artifacts.
     * Updates the graph with artifact nodes.
     */
    public void completeRenderPhase(String requestId, String artifactId,
                                       String artifactType, String uri, String hash) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) return;

        // Emit execution state transition
        tracer.traceExecutionState(requestId, "EXECUTING", "COMPLETING",
                "Render completed, finalizing", "RequestLifecycleEngine");

        // Emit artifact node
        tracer.traceArtifact(requestId, artifactId, artifactType, uri, hash);

        // Complete the graph
        tracer.completeGraph(requestId);

        // Persist the graph
        UnifiedRequestGraph completedGraph = tracer.getGraph(requestId);
        if (completedGraph != null) {
            graphRepository.save(completedGraph);
            log.info("[{}] UEEG persisted: {}", requestId, completedGraph.getSummary());
        }
    }

    /**
     * Fail the request lifecycle.
     * Updates the graph with failure state.
     */
    public void failRequest(String requestId, String reason) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) return;

        // Emit execution state transition
        tracer.traceExecutionState(requestId, "EXECUTING", "FAILED",
                "Request failed: " + reason, "RequestLifecycleEngine");

        // Fail the graph
        tracer.failGraph(requestId, reason);

        // Persist the graph
        UnifiedRequestGraph failedGraph = tracer.getGraph(requestId);
        if (failedGraph != null) {
            graphRepository.save(failedGraph);
            log.warn("[{}] UEEG failed: {}", requestId, reason);
        }
    }

    /**
     * Get the graph for a request.
     */
    public UnifiedRequestGraph getGraph(String requestId) {
        return tracer.getGraph(requestId);
    }

    /**
     * Get the graph for a job.
     */
    public UnifiedRequestGraph getGraphByJobId(String jobId) {
        return graphRepository.loadByJobId(jobId).orElse(null);
    }

    /**
     * Replay a request from its graph.
     * Returns the execution path for debugging.
     */
    public java.util.List<GraphNode> replayRequest(String requestId) {
        UnifiedRequestGraph graph = tracer.getGraph(requestId);
        if (graph == null) {
            graph = graphRepository.loadByRequestId(requestId).orElse(null);
        }
        if (graph == null) {
            throw new IllegalStateException("No graph found for request: " + requestId);
        }
        return graph.getExecutionPath();
    }
}
