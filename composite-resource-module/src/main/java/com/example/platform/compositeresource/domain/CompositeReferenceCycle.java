package com.example.platform.compositeresource.domain;

import java.util.List;

public record CompositeReferenceCycle(List<CompositeReferenceNodeId> nodes) {
    public CompositeReferenceCycle {
        if (nodes == null || nodes.size() < 2 || !nodes.getFirst().equals(nodes.getLast())) {
            throw new IllegalArgumentException("CompositeReferenceCycle requires a closed ordered path");
        }
        nodes = List.copyOf(nodes);
    }
}
