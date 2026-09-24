package com.example.platform.workerfabric.domain;

import java.util.Collection;
import java.util.Objects;

/** Platform runtime seam that delegates only to the canonical Native Pull matcher. */
public final class NativePullAdmissionPort {
    private final CentralWorkMatcher matcher;
    public NativePullAdmissionPort(AtomicAssignmentGrantBoundary grants) { this.matcher = new CentralWorkMatcher(Objects.requireNonNull(grants)); }
    public RequestWorkResult admit(RequestWork request, RequestWorkValidationContext context,
            Collection<PendingNativeWorkCandidate> candidates) {
        return matcher.match(request, context, candidates);
    }
}
