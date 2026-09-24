package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import java.util.Set;
import java.util.List;

/** Server-owned resolution of stable asset/input references for one scope. */
public interface CompositionResourceResolver {
    ResourceResolution resolve(TemplateWorkflow workflow, String tenantId, String workspaceId);

    record ResourceResolution(Set<String> availableAssets, List<String> references) {
        public ResourceResolution { availableAssets = availableAssets == null ? Set.of() : Set.copyOf(availableAssets); }
        public ResourceResolution(Set<String> availableAssets) { this(availableAssets, List.copyOf(availableAssets == null ? Set.<String>of() : availableAssets)); }
    }
}
