package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.ValidationResult;

/** Adapter boundary for a future durable runtime; this batch never invokes it. */
public record ValidatedCompositionRequest(String tenantId, String workspaceId, String compositionId, String version, ValidationResult validation) {
    public ValidatedCompositionRequest { if (tenantId == null || workspaceId == null || compositionId == null || version == null || validation == null) throw new IllegalArgumentException("validated request is incomplete"); if (!validation.ready()) throw new IllegalArgumentException("only ready compositions may cross the runtime adapter"); }
}
