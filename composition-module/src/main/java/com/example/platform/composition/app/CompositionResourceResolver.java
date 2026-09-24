package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import java.util.Set;

/** Server-owned resolution of stable asset/input references for one scope. */
public interface CompositionResourceResolver {
    ResourceResolution resolve(TemplateWorkflow workflow, String tenantId, String workspaceId);

    record ResourceResolution(Set<String> availableAssets) {
        public ResourceResolution { availableAssets = availableAssets == null ? Set.of() : Set.copyOf(availableAssets); }
    }
}
