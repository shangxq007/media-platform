package com.example.platform.workerfabric.domain;

import java.util.List;

/** Polling integration mechanic; cadence and cursor policy are not capability semantics. */
@FunctionalInterface
public interface RemotePollingObserverPort<Q> {

    List<ExecutionObservation> poll(Q pollingRequest);
}
