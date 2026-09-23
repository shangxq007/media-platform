package com.example.platform.composition.app;

import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;

/** Canonical execution seam for a validated Composition task. */
public interface CompositionExecutionPort {
    ProviderExecutionOutput execute(CompositionExecutionRequest request);
}
