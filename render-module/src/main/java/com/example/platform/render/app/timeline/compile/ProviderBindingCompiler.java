package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles a LogicalCapabilityGraph into a ProviderBindingPlan.
 *
 * <p>Provider binding assigns a provider to each capability node based on:
 * <ul>
 *   <li>Capability match: provider declares the required capabilities</li>
 *   <li>Status eligibility: based on binding mode (PRODUCTION, MANUAL, EXPERIMENT)</li>
 *   <li>Tool availability: provider tool/binary is locally available</li>
 *   <li>Priority + score: lower score = preferred (P0 PRODUCTION beats P1 POC)</li>
 * </ul>
 *
 * <p>This compiler does NOT execute providers. It only records binding decisions.
 * All decisions are deterministic for the same inputs.</p>
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
@Service
public class ProviderBindingCompiler {

    private static final Logger log = LoggerFactory.getLogger(ProviderBindingCompiler.class);

    /**
     * Compile a LogicalCapabilityGraph into a ProviderBindingPlan.
     *
     * @param capabilityGraph the source capability graph
     * @param candidates      available provider candidates
     * @param bindingMode     binding mode: PRODUCTION, MANUAL, EXPERIMENT
     * @return the binding plan with decisions for all nodes
     */
    public ProviderBindingPlan compile(
            LogicalCapabilityGraph capabilityGraph,
            List<ProviderCandidate> candidates,
            String bindingMode) {

        if (capabilityGraph == null) {
            throw TimelineCompileException.missingField("LogicalCapabilityGraph");
        }
        if (candidates == null) {
            candidates = List.of();
        }
        if (bindingMode == null || bindingMode.isBlank()) {
            bindingMode = "PRODUCTION";
        }

        List<ProviderBindingNode> nodes = new ArrayList<>();
        for (LogicalCapabilityNode capNode : capabilityGraph.nodes()) {
            ProviderBindingNode bindingNode = bindNode(capNode, candidates, bindingMode);
            nodes.add(bindingNode);
        }

        List<ProviderBindingEdge> edges = capabilityGraph.edges().stream()
                .map(e -> new ProviderBindingEdge(
                        e.edgeId(), e.sourceNodeId(), e.targetNodeId(), e.type()))
                .toList();

        boolean allBound = nodes.stream().allMatch(ProviderBindingNode::isBound);
        boolean hasFailures = nodes.stream().anyMatch(ProviderBindingNode::isFailed);

        ProviderBindingPlanId planId = ProviderBindingPlanId.fromCapabilityGraphId(
                capabilityGraph.graphId());

        log.info("Provider binding compiled: timelineId={} mode={} nodes={} bound={} failed={}",
                capabilityGraph.timelineId(), bindingMode,
                nodes.size(),
                nodes.stream().filter(ProviderBindingNode::isBound).count(),
                nodes.stream().filter(ProviderBindingNode::isFailed).count());

        return new ProviderBindingPlan(
                planId,
                capabilityGraph.graphId(),
                capabilityGraph.timelineId(),
                List.copyOf(nodes),
                List.copyOf(edges),
                bindingMode,
                allBound,
                hasFailures);
    }

    /**
     * Bind a single capability node to the best matching provider.
     */
    private ProviderBindingNode bindNode(
            LogicalCapabilityNode capNode,
            List<ProviderCandidate> candidates,
            String bindingMode) {

        List<String> requiredCaps = capNode.requirement() != null
                ? capNode.requirement().requiredCapabilities()
                : List.of();

        // Filter candidates that declare all required capabilities
        List<ProviderCandidate> matching = candidates.stream()
                .filter(c -> c.capabilities().containsAll(requiredCaps))
                .filter(c -> !c.notFor().stream().anyMatch(requiredCaps::contains))
                .collect(Collectors.toList());

        // Filter by binding mode eligibility
        List<ProviderCandidate> eligible = matching.stream()
                .filter(c -> isEligible(c, bindingMode))
                .collect(Collectors.toList());

        // Build bound provider refs for all candidates (for traceability)
        List<BoundProviderRef> allCandidates = matching.stream()
                .map(c -> toBoundProviderRef(c, eligible.contains(c), requiredCaps))
                .sorted(Comparator.comparingInt(BoundProviderRef::score))
                .toList();

        // Select best eligible candidate
        List<ProviderCandidate> best = eligible.stream()
                .sorted(Comparator.comparingInt(c -> computeScore(c, bindingMode)))
                .toList();

        ProviderBindingDecision decision;

        if (requiredCaps.isEmpty()) {
            // No capabilities required — auto-bind with no provider
            decision = ProviderBindingDecision.bound(
                    capNode.nodeId(), capNode.artifactNodeType().name(),
                    requiredCaps, null, allCandidates,
                    "No capabilities required");
        } else if (best.isEmpty()) {
            // No eligible provider
            ProviderBindingFailureReason reason = determineFailureReason(matching, eligible, bindingMode);
            decision = ProviderBindingDecision.failed(
                    capNode.nodeId(), capNode.artifactNodeType().name(),
                    requiredCaps, mapStatus(reason), reason, allCandidates,
                    "No eligible provider for capabilities: " + requiredCaps);
        } else {
            ProviderCandidate selected = best.get(0);
            BoundProviderRef ref = toBoundProviderRef(selected, true, requiredCaps);
            decision = ProviderBindingDecision.bound(
                    capNode.nodeId(), capNode.artifactNodeType().name(),
                    requiredCaps, ref, allCandidates,
                    "Bound to " + selected.name() + " (score=" + ref.score() + ")");
        }

        return new ProviderBindingNode(
                capNode.nodeId(),
                capNode.artifactNodeType(),
                capNode.label(),
                requiredCaps,
                decision);
    }

    /**
     * Check if a candidate is eligible for the given binding mode.
     */
    private boolean isEligible(ProviderCandidate candidate, String bindingMode) {
        return switch (bindingMode) {
            case "PRODUCTION" -> candidate.status().isProductionDispatchEligible()
                    && candidate.autoDispatch();
            case "MANUAL" -> candidate.status().canBeConfiguredForDispatch();
            case "EXPERIMENT" -> candidate.status().canBeConfiguredForDispatch();
            default -> false;
        };
    }

    /**
     * Compute a binding score for a candidate. Lower = preferred.
     */
    private int computeScore(ProviderCandidate candidate, String bindingMode) {
        int score = 0;

        // Status score
        score += switch (candidate.status()) {
            case PRODUCTION -> 0;
            case OPTIONAL -> 100;
            case POC -> 200;
            case HOLD -> 300;
            case SPIKE -> 400;
            default -> 500;
        };

        // Priority score
        score += switch (candidate.priority()) {
            case "P0" -> 0;
            case "P1" -> 10;
            case "P2" -> 20;
            case "P3" -> 30;
            default -> 40;
        };

        // Tool availability penalty
        if (!candidate.toolAvailable()) {
            score += 1000;
        }

        return score;
    }

    /**
     * Determine the failure reason when no provider is found.
     */
    private ProviderBindingFailureReason determineFailureReason(
            List<ProviderCandidate> matching,
            List<ProviderCandidate> eligible,
            String bindingMode) {
        if (matching.isEmpty()) {
            return ProviderBindingFailureReason.REQUIRED_CAPABILITY_MISSING;
        }
        if (eligible.isEmpty()) {
            // Check why they were filtered
            boolean anyProduction = matching.stream().anyMatch(c -> c.status().isProductionDispatchEligible());
            if (!anyProduction && "PRODUCTION".equals(bindingMode)) {
                return ProviderBindingFailureReason.PROVIDER_NOT_PRODUCTION_ELIGIBLE;
            }
            boolean anyToolAvailable = matching.stream().anyMatch(ProviderCandidate::toolAvailable);
            if (!anyToolAvailable) {
                return ProviderBindingFailureReason.TOOL_UNAVAILABLE;
            }
            return ProviderBindingFailureReason.PROVIDER_DISABLED;
        }
        return ProviderBindingFailureReason.MULTIPLE_PROVIDERS_AMBIGUOUS;
    }

    /**
     * Map failure reason to binding status.
     */
    private ProviderBindingStatus mapStatus(ProviderBindingFailureReason reason) {
        return switch (reason) {
            case REQUIRED_CAPABILITY_MISSING -> ProviderBindingStatus.UNSUPPORTED;
            case PROVIDER_DISABLED -> ProviderBindingStatus.DISABLED;
            case PROVIDER_NOT_PRODUCTION_ELIGIBLE -> ProviderBindingStatus.NOT_PRODUCTION_ELIGIBLE;
            case TOOL_UNAVAILABLE -> ProviderBindingStatus.TOOL_UNAVAILABLE;
            case PROVIDER_STATUS_BLOCKED -> ProviderBindingStatus.FAILED_CLOSED;
            case PROVIDER_AUTO_DISPATCH_DISABLED -> ProviderBindingStatus.MANUAL_ONLY;
            case MULTIPLE_PROVIDERS_AMBIGUOUS -> ProviderBindingStatus.AMBIGUOUS;
            case PROVIDER_TYPE_UNSUPPORTED -> ProviderBindingStatus.UNSUPPORTED;
            case OPENFX_REQUIRES_HOST -> ProviderBindingStatus.UNSUPPORTED;
            case EXECUTION_ENVIRONMENT_UNSUPPORTED -> ProviderBindingStatus.FAILED_CLOSED;
        };
    }

    /**
     * Convert a candidate to a BoundProviderRef for decision tracking.
     */
    private BoundProviderRef toBoundProviderRef(
            ProviderCandidate candidate, boolean eligible, List<String> requiredCaps) {
        int score = computeScore(candidate, "PRODUCTION");
        return new BoundProviderRef(
                candidate.name(),
                candidate.status(),
                candidate.type(),
                candidate.priority(),
                candidate.autoDispatch(),
                candidate.toolAvailable(),
                candidate.toolVersion(),
                eligible ? score : score + 10000);
    }

    /**
     * Provider candidate for binding — mirrors ProviderMetadata without
     * coupling to the infrastructure layer during compile.
     */
    public record ProviderCandidate(
            String name,
            com.example.platform.render.infrastructure.ProviderStatus status,
            com.example.platform.render.infrastructure.ProviderType type,
            String priority,
            boolean autoDispatch,
            boolean toolAvailable,
            String toolVersion,
            List<String> capabilities,
            List<String> notFor) {

        /**
         * Create from ProviderMetadata + tool availability.
         */
        public static ProviderCandidate fromMetadata(
                com.example.platform.render.infrastructure.ProviderMetadata metadata,
                boolean toolAvailable, String toolVersion) {
            return new ProviderCandidate(
                    metadata.name(),
                    metadata.status(),
                    metadata.providerType(),
                    metadata.priority(),
                    metadata.autoDispatch(),
                    toolAvailable,
                    toolVersion,
                    List.copyOf(metadata.enabledCapabilities()),
                    List.copyOf(metadata.notFor()));
        }
    }
}
