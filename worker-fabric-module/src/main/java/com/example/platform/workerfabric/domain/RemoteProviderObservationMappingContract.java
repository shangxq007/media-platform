package com.example.platform.workerfabric.domain;

/** Maps a remote-provider status envelope to normalized, non-authoritative evidence. */
@FunctionalInterface
public interface RemoteProviderObservationMappingContract<S> {

    ExecutionObservation normalize(S remoteProviderStatusEnvelope);
}
