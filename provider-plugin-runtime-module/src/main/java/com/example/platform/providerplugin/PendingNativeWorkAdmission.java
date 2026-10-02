package com.example.platform.providerplugin;

import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkResult;
import com.example.platform.workerfabric.domain.RequestWorkValidationContext;
import java.util.List;
import java.util.Objects;

/**
 * P2-5b-2a-2a-3c: drives one Native Pull admission from the bound graph and the provider's
 * declarations.
 *
 * <p>Pure and stateless: it projects the pending work for the graph
 * ({@link PendingNativeWorkProjection}), hands exactly those candidates to the canonical
 * {@link NativePullAdmissionPort} and returns that port's {@link RequestWorkResult} unchanged. It
 * owns no scheduling policy, no candidate store and no result interpretation — CAN_RUN, WHICH_IS_BEST
 * and every terminal outcome remain the matcher's authority, and the port is supplied by the caller
 * so this helper holds no persistence or transaction boundary of its own.
 *
 * <p><b>Fail closed by propagation.</b> Nothing is caught or rewritten: a projection that rejects the
 * request (undeclared requirement, no statically feasible candidate) surfaces
 * {@link PendingNativeWorkProjectionException}, and any matcher side effect failure surfaces whatever
 * the port raises. An unprojectable or non-admittable request never becomes a silent
 * {@code NoWork}.
 */
public final class PendingNativeWorkAdmission {

    private PendingNativeWorkAdmission() {
    }

    /**
     * Projects the graph's pending work and admits it through the canonical Native Pull port.
     *
     * @param admissionPort the caller-owned port (constructed over the grant boundary)
     * @return the port's exact result — granted, no-work, rejected or reprobe-required
     * @throws PendingNativeWorkProjectionException if the pending work cannot be projected
     */
    public static RequestWorkResult admit(
            NativePullAdmissionPort admissionPort,
            ProviderBoundExecutableTaskGraph graph,
            ProviderPluginContribution contribution,
            RequestWork requestWork,
            RequestWorkValidationContext validationContext) {
        Objects.requireNonNull(admissionPort, "admissionPort");
        Objects.requireNonNull(requestWork, "requestWork");
        Objects.requireNonNull(validationContext, "validationContext");
        List<PendingNativeWorkCandidate> candidates =
                PendingNativeWorkProjection.projectAll(graph, contribution);
        return admissionPort.admit(requestWork, validationContext, candidates);
    }
}
