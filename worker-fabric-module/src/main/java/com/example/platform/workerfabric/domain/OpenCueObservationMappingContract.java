package com.example.platform.workerfabric.domain;

/** Maps a farm-specific status envelope to normalized, non-authoritative evidence. */
@FunctionalInterface
public interface OpenCueObservationMappingContract<S> {

    ExecutionObservation normalize(S openCueStatusEnvelope);
}
