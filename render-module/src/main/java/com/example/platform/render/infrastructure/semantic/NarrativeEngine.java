package com.example.platform.render.infrastructure.semantic;

import com.example.platform.render.infrastructure.unified.GraphNode;
import com.example.platform.render.infrastructure.unified.UnifiedRequestGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Engine for generating human-readable narratives from UEEG graphs.
 * 
 * <p>This is a READ-ONLY transformation layer. It does NOT modify the UEEG.
 * 
 * <p>Features:
 * <ul>
 *   <li>Deterministic output</li>
 *   <li>Localization-ready text</li>
 *   <li>AI-LLM consumable</li>
 *   <li>Step-by-step reasoning</li>
 *   <li>Causal chain explanation</li>
 * </ul>
 */
@Service
public class NarrativeEngine {

    private static final Logger log = LoggerFactory.getLogger(NarrativeEngine.class);

    /**
     * Generate a SemanticGraph from a UnifiedRequestGraph.
     */
    public SemanticGraph generateSemanticGraph(UnifiedRequestGraph ueeg) {
        log.info("Generating semantic graph for request {}", ueeg.requestId());

        // Determine user intent
        SemanticGraph.UserIntent userIntent = inferUserIntent(ueeg);

        // Determine product outcome
        SemanticGraph.ProductOutcome productOutcome = inferProductOutcome(ueeg);

        // Generate explanation
        String explanation = generateExplanation(ueeg, productOutcome);

        // Generate cost narrative
        CostNarrative costNarrative = generateCostNarrative(ueeg);

        // Generate decision narrative
        DecisionNarrative decisionNarrative = generateDecisionNarrative(ueeg);

        // Generate failure narrative (if applicable)
        FailureNarrative failureNarrative = productOutcome == SemanticGraph.ProductOutcome.FAILED
                ? generateFailureNarrative(ueeg) : null;

        // Map UEEG nodes to semantic nodes
        List<SemanticNode> semanticNodes = mapNodesToSemantic(ueeg);

        // Map UEEG edges to semantic edges
        List<SemanticEdge> semanticEdges = mapEdgesToSemantic(ueeg);

        return SemanticGraph.fromUeeg(
                ueeg.requestId(),
                ueeg.jobId(),
                userIntent,
                productOutcome,
                explanation,
                costNarrative,
                decisionNarrative,
                failureNarrative,
                semanticNodes,
                semanticEdges
        );
    }

    /**
     * Generate a ProductViewModel from a SemanticGraph.
     */
    public ProductViewModel generateProductViewModel(SemanticGraph semanticGraph) {
        return ProductViewModel.fromSemanticGraph(semanticGraph);
    }

    /**
     * Generate a simplified explanation for AI consumption.
     */
    public String generateAiExplanation(UnifiedRequestGraph ueeg) {
        SemanticGraph semanticGraph = generateSemanticGraph(ueeg);
        return semanticGraph.toAiPrompt();
    }

    // ---------------------------------------------------------------------------
    // Intent Inference
    // ---------------------------------------------------------------------------

    private SemanticGraph.UserIntent inferUserIntent(UnifiedRequestGraph ueeg) {
        // Check metadata for intent hints
        Map<String, Object> metadata = ueeg.metadata();
        String profile = metadata.containsKey("profile") ? metadata.get("profile").toString() : null;
        boolean isBatch = metadata.containsKey("batch") && Boolean.TRUE.equals(metadata.get("batch"));

        return SemanticGraph.UserIntent.RENDER_VIDEO;
    }

    // ---------------------------------------------------------------------------
    // Outcome Inference
    // ---------------------------------------------------------------------------

    private SemanticGraph.ProductOutcome inferProductOutcome(UnifiedRequestGraph ueeg) {
        if (ueeg.status() == UnifiedRequestGraph.GraphStatus.COMPLETED) {
            return SemanticGraph.ProductOutcome.COMPLETED;
        }
        if (ueeg.status() == UnifiedRequestGraph.GraphStatus.FAILED) {
            return SemanticGraph.ProductOutcome.FAILED;
        }
        if (ueeg.status() == UnifiedRequestGraph.GraphStatus.CANCELLED) {
            return SemanticGraph.ProductOutcome.CANCELLED;
        }

        // Check the precomputed commercial admission projection.
        GraphNode billingNode = ueeg.getCommercialDecisionNode().orElse(null);
        if (billingNode != null && "DENIED".equals(billingNode.status())) {
            return SemanticGraph.ProductOutcome.REJECTED;
        }

        return SemanticGraph.ProductOutcome.IN_PROGRESS;
    }

    // ---------------------------------------------------------------------------
    // Explanation Generation
    // ---------------------------------------------------------------------------

    private String generateExplanation(UnifiedRequestGraph ueeg, SemanticGraph.ProductOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> "Your render job completed successfully. " +
                    "The video has been generated and is ready for download.";
            case FAILED -> {
                FailureNarrative failure = generateFailureNarrative(ueeg);
                yield "Your render job failed. " + failure.rootCause();
            }
            case REJECTED -> {
                GraphNode billingNode = ueeg.getCommercialDecisionNode().orElse(null);
                String reason = billingNode != null
                        ? billingNode.getStringData("reasonCode", "Unknown reason")
                        : "Unknown reason";
                yield "Your render job was rejected. " + reason;
            }
            case IN_PROGRESS -> "Your render job is currently being processed.";
            case QUEUED -> "Your render job is queued and will start soon.";
            case CANCELLED -> "Your render job was cancelled.";
        };
    }

    // ---------------------------------------------------------------------------
    // Cost Narrative
    // ---------------------------------------------------------------------------

    private CostNarrative generateCostNarrative(UnifiedRequestGraph ueeg) {
        // Commercial admission carries no price and provider ExecutionCost is not customer price.
        // A cost narrative requires a separately supplied typed cost projection.
        return null;
    }

    // ---------------------------------------------------------------------------
    // Decision Narrative
    // ---------------------------------------------------------------------------

    private DecisionNarrative generateDecisionNarrative(UnifiedRequestGraph ueeg) {
        List<DecisionNarrative.DecisionStep> steps = new ArrayList<>();

        // Neutral commercial admission decision
        GraphNode billingNode = ueeg.getCommercialDecisionNode().orElse(null);
        if (billingNode != null) {
            steps.add(new DecisionNarrative.DecisionStep(
                    "Commercial admission check",
                    billingNode.getStringData("reasonCode", "Checked"),
                    billingNode.status()
            ));
        }

        // Policy decision
        GraphNode policyNode = ueeg.getPolicyDecisionNode().orElse(null);
        if (policyNode != null) {
            steps.add(new DecisionNarrative.DecisionStep(
                    "Policy constraints evaluation",
                    policyNode.getStringData("denyReason", "No violations"),
                    policyNode.status()
            ));
        }

        // Provider decision
        GraphNode providerNode = ueeg.getProviderDecisionNode().orElse(null);
        if (providerNode != null) {
            boolean fallback = providerNode.getBooleanData("fallbackTriggered", false);
            String reason = fallback
                    ? "Primary provider unavailable, fallback triggered"
                    : "Provider selected based on capabilities";
            steps.add(new DecisionNarrative.DecisionStep(
                    "Execution engine selection",
                    reason,
                    providerNode.status()
            ));
        }

        String reasoning = "The system evaluated billing eligibility, policy constraints, " +
                "and selected the appropriate execution engine for your render job.";

        return DecisionNarrative.create(steps, reasoning);
    }

    // ---------------------------------------------------------------------------
    // Failure Narrative
    // ---------------------------------------------------------------------------

    private FailureNarrative generateFailureNarrative(UnifiedRequestGraph ueeg) {
        // Check commercial admission failure
        GraphNode billingNode = ueeg.getCommercialDecisionNode().orElse(null);
        if (billingNode != null && "DENIED".equals(billingNode.status())) {
            return FailureNarrative.commercialAdmissionFailure(
                    billingNode.getStringData("reasonCode", "UNKNOWN"),
                    billingNode.getStringData("reasonCode", "Unknown reason")
            );
        }

        // Check policy failure
        GraphNode policyNode = ueeg.getPolicyDecisionNode().orElse(null);
        if (policyNode != null && "DENIED".equals(policyNode.status())) {
            return FailureNarrative.policyFailure(
                    "Policy constraint",
                    policyNode.getStringData("denyReason", "Unknown policy violation")
            );
        }

        // Default provider failure
        GraphNode providerNode = ueeg.getProviderDecisionNode().orElse(null);
        String provider = providerNode != null
                ? providerNode.getStringData("selectedProvider", "Unknown")
                : "Unknown";

        return FailureNarrative.providerFailure(provider, "Render execution failed");
    }

    // ---------------------------------------------------------------------------
    // Node Mapping
    // ---------------------------------------------------------------------------

    private List<SemanticNode> mapNodesToSemantic(UnifiedRequestGraph ueeg) {
        List<SemanticNode> semanticNodes = new ArrayList<>();

        for (GraphNode node : ueeg.toNodeList()) {
            SemanticNode semanticNode = switch (node.type()) {
                case EXECUTION_STATE_NODE -> SemanticNode.executionState(
                        node.nodeId(),
                        node.getStringData("fromState", "Unknown"),
                        node.getStringData("toState", "Unknown"),
                        node.getStringData("reason", "")
                );
                case COMMERCIAL_DECISION_NODE -> SemanticNode.commercialDecision(
                        node.nodeId(),
                        node.status(),
                        node.getStringData("reasonCode", "UNKNOWN")
                );
                case POLICY_DECISION_NODE -> SemanticNode.policyDecision(
                        node.nodeId(),
                        node.getBooleanData("allowed", true),
                        node.getStringData("denyReason", ""),
                        node.getDoubleData("discountPercent", 0)
                );
                case PROVIDER_DECISION_NODE -> SemanticNode.providerDecision(
                        node.nodeId(),
                        node.getStringData("selectedProvider", "Unknown"),
                        node.getStringData("reason", ""),
                        node.getBooleanData("fallbackTriggered", false)
                );
                case ARTIFACT_NODE -> SemanticNode.artifact(
                        node.nodeId(),
                        node.getStringData("artifactType", "Unknown"),
                        node.getStringData("uri", ""),
                        node.getStringData("hash", "")
                );
            };
            semanticNodes.add(semanticNode);
        }

        return semanticNodes;
    }

    // ---------------------------------------------------------------------------
    // Edge Mapping
    // ---------------------------------------------------------------------------

    private List<SemanticEdge> mapEdgesToSemantic(UnifiedRequestGraph ueeg) {
        List<SemanticEdge> semanticEdges = new ArrayList<>();

        for (var edge : ueeg.toEdgeList()) {
            String description = switch (edge.edgeType()) {
                case "TRIGGERS" -> "triggers next step";
                case "DECIDES" -> "informs decision";
                case "VALIDATES" -> "validates constraints";
                case "CONSUMES" -> "uses for selection";
                case "PRODUCES" -> "generates output";
                default -> "relates to";
            };

            semanticEdges.add(SemanticEdge.create(
                    edge.sourceNodeId(),
                    edge.targetNodeId(),
                    edge.edgeType().toLowerCase(),
                    description
            ));
        }

        return semanticEdges;
    }
}
