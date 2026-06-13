package com.example.platform.render.infrastructure.semantic;

import com.example.platform.render.infrastructure.unified.UnifiedGraphRepository;
import com.example.platform.render.infrastructure.unified.UnifiedRequestGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for semantic explanations of render jobs.
 * 
 * <p>Provides human-readable and AI-consumable explanations
 * of render job execution.
 */
@RestController
@RequestMapping("/api/v1/semantic")
public class SemanticApi {

    private static final Logger log = LoggerFactory.getLogger(SemanticApi.class);

    private final NarrativeEngine narrativeEngine;
    private final UnifiedGraphRepository graphRepository;

    public SemanticApi(NarrativeEngine narrativeEngine, UnifiedGraphRepository graphRepository) {
        this.narrativeEngine = narrativeEngine;
        this.graphRepository = graphRepository;
    }

    /**
     * Get semantic explanation for a render job.
     */
    @GetMapping("/explain/{jobId}")
    public SemanticExplanationResponse explain(@PathVariable String jobId) {
        log.info("Generating semantic explanation for job {}", jobId);

        UnifiedRequestGraph graph = graphRepository.loadByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No graph found for job: " + jobId));

        SemanticGraph semanticGraph = narrativeEngine.generateSemanticGraph(graph);
        ProductViewModel viewModel = narrativeEngine.generateProductViewModel(semanticGraph);

        return new SemanticExplanationResponse(
                jobId,
                semanticGraph.explanation(),
                semanticGraph.costNarrative() != null ? semanticGraph.costNarrative().explanation() : null,
                semanticGraph.failureNarrative() != null ? semanticGraph.failureNarrative().explanation() : null,
                semanticGraph.decisionNarrative() != null ? semanticGraph.decisionNarrative().summary() : null,
                viewModel
        );
    }

    /**
     * Get AI-consumable explanation for a render job.
     */
    @GetMapping("/explain/{jobId}/ai")
    public AiExplanationResponse explainForAi(@PathVariable String jobId) {
        log.info("Generating AI explanation for job {}", jobId);

        UnifiedRequestGraph graph = graphRepository.loadByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No graph found for job: " + jobId));

        String aiPrompt = narrativeEngine.generateAiExplanation(graph);

        return new AiExplanationResponse(jobId, aiPrompt);
    }

    /**
     * Get simplified status for a render job.
     */
    @GetMapping("/status/{jobId}")
    public StatusResponse getStatus(@PathVariable String jobId) {
        UnifiedRequestGraph graph = graphRepository.loadByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No graph found for job: " + jobId));

        SemanticGraph semanticGraph = narrativeEngine.generateSemanticGraph(graph);

        return new StatusResponse(
                jobId,
                semanticGraph.productOutcome().label(),
                semanticGraph.productOutcome().severity(),
                semanticGraph.explanation()
        );
    }

    /**
     * Get cost explanation for a render job.
     */
    @GetMapping("/cost/{jobId}")
    public CostExplanationResponse getCostExplanation(@PathVariable String jobId) {
        UnifiedRequestGraph graph = graphRepository.loadByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No graph found for job: " + jobId));

        SemanticGraph semanticGraph = narrativeEngine.generateSemanticGraph(graph);
        CostNarrative costNarrative = semanticGraph.costNarrative();

        if (costNarrative == null) {
            return new CostExplanationResponse(jobId, "No cost data available", 0, 0, "USD");
        }

        return new CostExplanationResponse(
                jobId,
                costNarrative.explanation(),
                costNarrative.estimatedCost(),
                costNarrative.actualCost(),
                costNarrative.currency()
        );
    }

    // ---------------------------------------------------------------------------
    // Response Types
    // ---------------------------------------------------------------------------

    public record SemanticExplanationResponse(
            String jobId,
            String explanation,
            String costExplanation,
            String failureExplanation,
            String decisionSummary,
            ProductViewModel viewModel
    ) {}

    public record AiExplanationResponse(
            String jobId,
            String aiPrompt
    ) {}

    public record StatusResponse(
            String jobId,
            String status,
            String severity,
            String explanation
    ) {}

    public record CostExplanationResponse(
            String jobId,
            String explanation,
            double estimatedCost,
            double actualCost,
            String currency
    ) {}
}
