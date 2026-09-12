package com.example.platform.observability.app;

import com.example.platform.observability.context.ObservationContext;
import com.example.platform.observability.context.TraceKeys;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Observability-owned adapter over the existing per-thread logging mechanism. */
@Component
public final class MdcObservationContext implements ObservationContext {
    @Override public Snapshot snapshot() {
        return new Snapshot(MDC.get(TraceKeys.TRACE_ID), MDC.get(TraceKeys.REQUEST_ID), MDC.get(TraceKeys.PRINCIPAL));
    }
}
