package com.example.platform.compositeresource.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class CompositeResourceDiff {
    private final List<CompositeResourceChange> changes;

    private CompositeResourceDiff(List<CompositeResourceChange> changes) {
        this.changes = List.copyOf(changes);
    }

    public static CompositeResourceDiff between(
            CompositeResourceVersion before,
            CompositeResourceVersion after) {
        if (before == null || after == null || !before.resourceId().equals(after.resourceId())) {
            throw new CompositeResourceDiffException(
                    CompositeResourceDiffErrorCode.INVALID_DIFF_INPUT,
                    "CompositeResourceDiff requires two exact versions of the same resource");
        }
        List<CompositeResourceChange> changes = new ArrayList<>();
        Map<String, SemanticFacet> beforeFacets = facetsById(before.facets());
        Map<String, SemanticFacet> afterFacets = facetsById(after.facets());
        TreeSet<String> facetIds = new TreeSet<>(beforeFacets.keySet());
        facetIds.addAll(afterFacets.keySet());
        for (String facetValue : facetIds) {
            SemanticFacet left = beforeFacets.get(facetValue);
            SemanticFacet right = afterFacets.get(facetValue);
            SemanticFacetId facetId = left != null ? left.facetId() : right.facetId();
            if (left == null) {
                changes.add(new CompositeResourceChange.FacetAdded(facetId));
            } else if (right == null) {
                changes.add(new CompositeResourceChange.FacetRemoved(facetId));
            } else {
                compareFacet(left, right, changes);
            }
        }
        return new CompositeResourceDiff(changes);
    }

    public List<CompositeResourceChange> changes() {
        return changes;
    }

    private static void compareFacet(
            SemanticFacet before,
            SemanticFacet after,
            List<CompositeResourceChange> changes) {
        SemanticFacetId facetId = before.facetId();
        if (!before.facetType().equals(after.facetType())) {
            changes.add(new CompositeResourceChange.FacetTypeChanged(
                    facetId, before.facetType(), after.facetType()));
        }
        if (before.collectionSemantics() != after.collectionSemantics()) {
            changes.add(new CompositeResourceChange.CollectionSemanticsChanged(
                    facetId, before.collectionSemantics(), after.collectionSemantics()));
        }

        Map<String, SemanticComponent> beforeComponents = componentsById(before.components());
        Map<String, SemanticComponent> afterComponents = componentsById(after.components());
        TreeSet<String> componentIds = new TreeSet<>(beforeComponents.keySet());
        componentIds.addAll(afterComponents.keySet());
        for (String componentValue : componentIds) {
            SemanticComponent left = beforeComponents.get(componentValue);
            SemanticComponent right = afterComponents.get(componentValue);
            CompositeComponentId componentId = left != null ? left.componentId() : right.componentId();
            if (left == null) {
                changes.add(new CompositeResourceChange.ComponentAdded(facetId, componentId));
            } else if (right == null) {
                changes.add(new CompositeResourceChange.ComponentRemoved(facetId, componentId));
            } else if (variant(left) != variant(right)) {
                changes.add(new CompositeResourceChange.ComponentRebound(
                        facetId, componentId, variant(left), variant(right)));
            } else if (left instanceof ExternalSemanticComponent leftExternal
                    && right instanceof ExternalSemanticComponent rightExternal
                    && !leftExternal.pin().equals(rightExternal.pin())) {
                changes.add(new CompositeResourceChange.ExternalSemanticPinChanged(
                        facetId, componentId, leftExternal.pin(), rightExternal.pin()));
            } else if (left instanceof NestedCompositeResourceComponent leftNested
                    && right instanceof NestedCompositeResourceComponent rightNested
                    && !leftNested.pin().equals(rightNested.pin())) {
                changes.add(new CompositeResourceChange.NestedResourcePinChanged(
                        facetId, componentId, leftNested.pin(), rightNested.pin()));
            }
        }

        if (before.collectionSemantics() == ComponentCollectionSemantics.ORDERED
                && after.collectionSemantics() == ComponentCollectionSemantics.ORDERED) {
            for (SemanticComponent component : before.components()) {
                int from = indexOf(before.components(), component.componentId());
                int to = indexOf(after.components(), component.componentId());
                if (to >= 0 && from != to) {
                    changes.add(new CompositeResourceChange.ComponentMoved(
                            facetId, component.componentId(), from, to));
                }
            }
        }
    }

    private static Map<String, SemanticFacet> facetsById(List<SemanticFacet> facets) {
        Map<String, SemanticFacet> indexed = new TreeMap<>();
        facets.forEach(facet -> indexed.put(facet.facetId().value(), facet));
        return indexed;
    }

    private static Map<String, SemanticComponent> componentsById(List<SemanticComponent> components) {
        Map<String, SemanticComponent> indexed = new TreeMap<>();
        components.forEach(component -> indexed.put(component.componentId().value(), component));
        return indexed;
    }

    private static int indexOf(List<SemanticComponent> components, CompositeComponentId componentId) {
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index).componentId().equals(componentId)) {
                return index;
            }
        }
        return -1;
    }

    private static ComponentBindingVariant variant(SemanticComponent component) {
        return component instanceof ExternalSemanticComponent
                ? ComponentBindingVariant.EXTERNAL_SEMANTIC
                : ComponentBindingVariant.NESTED_COMPOSITE_RESOURCE;
    }
}
