package com.example.platform.workerfabric.domain;

/** External observation mechanics that produce normalized evidence only. */
@FunctionalInterface
public interface ObserverPlanePort<I> {

    ExecutionObservation observe(I externalEvidence);
}
