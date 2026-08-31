package com.example.platform.compositeresource.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class CompositeReferenceGraph {
    private final Set<CompositeReferenceNodeId> nodes;
    private final Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges;

    private CompositeReferenceGraph(
            Set<CompositeReferenceNodeId> nodes,
            Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges) {
        this.nodes = Collections.unmodifiableSet(new LinkedHashSet<>(nodes));
        Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> immutable = new LinkedHashMap<>();
        edges.forEach((node, targets) -> immutable.put(node, List.copyOf(targets)));
        this.edges = Collections.unmodifiableMap(immutable);
    }

    public static CompositeReferenceGraph fromResolvedVersions(
            Collection<CompositeResourceVersion> resolvedVersions,
            boolean completeClosure) {
        if (resolvedVersions == null) {
            throw new IllegalArgumentException("resolved exact versions are required");
        }
        Map<CompositeReferenceNodeId, CompositeResourceVersion> versions = new TreeMap<>();
        for (CompositeResourceVersion version : resolvedVersions) {
            if (version == null) {
                throw new IllegalArgumentException("resolved exact version must not be null");
            }
            CompositeReferenceNodeId node = nodeOf(version);
            if (versions.putIfAbsent(node, version) != null) {
                throw failure(CompositeReferenceErrorCode.DUPLICATE_NODE_IDENTITY,
                        "duplicate resolved reference node " + node.resourceId().value(), null);
            }
        }

        Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges = new TreeMap<>();
        versions.forEach((node, version) -> edges.put(node, nestedPins(version)));
        CompositeReferenceCycle cycle = findCycle(versions.keySet(), edges);
        if (cycle != null) {
            throw failure(CompositeReferenceErrorCode.CYCLE_DETECTED, "composite reference cycle detected", cycle);
        }

        if (completeClosure) {
            edges.forEach((source, targets) -> {
                for (CompositeResourceVersionPin target : targets) {
                    CompositeReferenceNodeId targetNode = nodeOf(target);
                    CompositeResourceVersion resolved = versions.get(targetNode);
                    if (resolved == null) {
                        throw failure(CompositeReferenceErrorCode.INCOMPLETE_REFERENCE_CLOSURE,
                                "exact referenced node is absent from complete closure", null);
                    }
                    if (!resolved.semanticContentDigest().matches(target.contentDigest())) {
                        throw failure(CompositeReferenceErrorCode.EXACT_PIN_DIGEST_MISMATCH,
                                "resolved exact node digest does not match nested pin", null);
                    }
                }
            });
        }
        return new CompositeReferenceGraph(versions.keySet(), edges);
    }

    public Set<CompositeReferenceNodeId> nodes() {
        return nodes;
    }

    public Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges() {
        return edges;
    }

    private static List<CompositeResourceVersionPin> nestedPins(CompositeResourceVersion version) {
        return version.facets().stream()
                .flatMap(facet -> facet.components().stream())
                .filter(NestedCompositeResourceComponent.class::isInstance)
                .map(NestedCompositeResourceComponent.class::cast)
                .map(NestedCompositeResourceComponent::pin)
                .sorted((left, right) -> nodeOf(left).compareTo(nodeOf(right)))
                .toList();
    }

    private static CompositeReferenceCycle findCycle(
            Set<CompositeReferenceNodeId> nodes,
            Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges) {
        Map<CompositeReferenceNodeId, Integer> state = new TreeMap<>();
        List<CompositeReferenceNodeId> path = new ArrayList<>();
        for (CompositeReferenceNodeId node : nodes) {
            CompositeReferenceCycle cycle = visit(node, nodes, edges, state, path);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static CompositeReferenceCycle visit(
            CompositeReferenceNodeId node,
            Set<CompositeReferenceNodeId> nodes,
            Map<CompositeReferenceNodeId, List<CompositeResourceVersionPin>> edges,
            Map<CompositeReferenceNodeId, Integer> state,
            List<CompositeReferenceNodeId> path) {
        if (state.getOrDefault(node, 0) == 2) {
            return null;
        }
        state.put(node, 1);
        path.add(node);
        for (CompositeResourceVersionPin pin : edges.getOrDefault(node, List.of())) {
            CompositeReferenceNodeId target = nodeOf(pin);
            if (!nodes.contains(target)) {
                continue;
            }
            if (state.getOrDefault(target, 0) == 1) {
                int start = path.indexOf(target);
                List<CompositeReferenceNodeId> cycleNodes = new ArrayList<>(path.subList(start, path.size()));
                cycleNodes.add(target);
                return new CompositeReferenceCycle(cycleNodes);
            }
            if (state.getOrDefault(target, 0) == 0) {
                CompositeReferenceCycle cycle = visit(target, nodes, edges, state, path);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        state.put(node, 2);
        return null;
    }

    private static CompositeReferenceNodeId nodeOf(CompositeResourceVersion version) {
        return new CompositeReferenceNodeId(version.resourceId(), version.versionId());
    }

    private static CompositeReferenceNodeId nodeOf(CompositeResourceVersionPin pin) {
        return new CompositeReferenceNodeId(pin.resourceId(), pin.versionId());
    }

    private static CompositeReferenceValidationException failure(
            CompositeReferenceErrorCode code,
            String message,
            CompositeReferenceCycle cycle) {
        return new CompositeReferenceValidationException(code, message, cycle);
    }
}
