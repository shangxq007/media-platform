package com.example.platform.runtime.mediatask;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.ProviderHardwareObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import java.util.Objects;
import java.util.Optional;

/**
 * P1-1A.6: produces the three authoritative admission evidence inputs for one bounded-V1 Native
 * Pull {@code RequestWork}.
 *
 * <p>Each input is independent and comes from its own authoritative source — a shared record never
 * lets one signal stand in for another. An absent input is returned as {@link Optional#empty()} so
 * the caller fails closed; nothing is defaulted.
 */
public interface AdmissionEvidenceProducer {

    Evidence produce(
            WorkerRuntimeId workerRuntimeId,
            PhysicalHostId physicalHostId,
            ProviderImplementationId providerImplementationId);

    /**
     * The three independent inputs. {@code runtimeSupportAdvertisement} also carries the frozen
     * {@code RuntimeLifecycleKind} the runtime must declare for admission.
     */
    record Evidence(
            Optional<WorkerRuntimeSupportAdvertisement> runtimeSupportAdvertisement,
            Optional<ProviderHardwareObservation> providerHardwareObservation,
            Optional<RuntimeDependencyObservation> runtimeDependencyObservation) {

        public Evidence {
            Objects.requireNonNull(runtimeSupportAdvertisement, "runtimeSupportAdvertisement");
            Objects.requireNonNull(providerHardwareObservation, "providerHardwareObservation");
            Objects.requireNonNull(runtimeDependencyObservation, "runtimeDependencyObservation");
        }
    }
}
