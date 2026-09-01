package com.example.platform.compositeresource.domain;

public sealed interface CompositeResourceChange permits
        CompositeResourceChange.FacetAdded,
        CompositeResourceChange.FacetRemoved,
        CompositeResourceChange.FacetTypeChanged,
        CompositeResourceChange.CollectionSemanticsChanged,
        CompositeResourceChange.ComponentAdded,
        CompositeResourceChange.ComponentRemoved,
        CompositeResourceChange.ComponentRebound,
        CompositeResourceChange.ComponentMoved,
        CompositeResourceChange.ExternalSemanticPinChanged,
        CompositeResourceChange.NestedResourcePinChanged {

    record FacetAdded(SemanticFacetId facetId) implements CompositeResourceChange {}

    record FacetRemoved(SemanticFacetId facetId) implements CompositeResourceChange {}

    record FacetTypeChanged(
            SemanticFacetId facetId,
            SemanticFacetTypeId before,
            SemanticFacetTypeId after) implements CompositeResourceChange {}

    record CollectionSemanticsChanged(
            SemanticFacetId facetId,
            ComponentCollectionSemantics before,
            ComponentCollectionSemantics after) implements CompositeResourceChange {}

    record ComponentAdded(
            SemanticFacetId facetId,
            CompositeComponentId componentId) implements CompositeResourceChange {}

    record ComponentRemoved(
            SemanticFacetId facetId,
            CompositeComponentId componentId) implements CompositeResourceChange {}

    record ComponentRebound(
            SemanticFacetId facetId,
            CompositeComponentId componentId,
            ComponentBindingVariant before,
            ComponentBindingVariant after) implements CompositeResourceChange {}

    record ComponentMoved(
            SemanticFacetId facetId,
            CompositeComponentId componentId,
            int fromIndex,
            int toIndex) implements CompositeResourceChange {}

    record ExternalSemanticPinChanged(
            SemanticFacetId facetId,
            CompositeComponentId componentId,
            ExactSemanticObjectPin before,
            ExactSemanticObjectPin after) implements CompositeResourceChange {}

    record NestedResourcePinChanged(
            SemanticFacetId facetId,
            CompositeComponentId componentId,
            CompositeResourceVersionPin before,
            CompositeResourceVersionPin after) implements CompositeResourceChange {}
}
