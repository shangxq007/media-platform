package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.CompatibilityEvidence.ReferenceKind;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.ArtifactRequirementKind;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.LoweringSupport;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.SandboxMode;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.Codec;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.CrossProviderBoundary;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderRuntime;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Canonical pure, deterministic, fail-closed Stage-1 legality authority.
 * It answers whether immutable provider metadata can represent a PhysicalPlanUnit.
 */
public final class CompatibilityKernel {

    private CompatibilityKernel() {
    }

    public static CompatibilityDecision evaluate(
            CompatibilityRequest request, ProviderCandidate candidate) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(candidate, "candidate");

        var unitId = request.physicalPlanUnit().stepId();
        if (candidate.staticCompatibility().knowledge()
                == ProviderStaticCompatibility.Knowledge.UNKNOWN) {
            return CompatibilityDecision.unknown(
                    request,
                    candidate,
                    List.of(evidence(
                            StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY,
                            ReferenceKind.PROVIDER_CONTRACT,
                            "provider-static-compatibility")));
        }
        if (candidate.staticCompatibility().loweringSupport() == LoweringSupport.UNKNOWN) {
            return CompatibilityDecision.unknown(
                    request,
                    candidate,
                    List.of(evidence(
                            StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY,
                            ReferenceKind.LOWERING,
                            unitId.value())));
        }

        EnumSet<StaticCompatibilityFailure> failures =
                EnumSet.noneOf(StaticCompatibilityFailure.class);
        List<CompatibilityEvidence> evidence = new ArrayList<>();

        evaluateProviderContract(candidate, failures, evidence);
        evaluateCapabilities(request, candidate, failures, evidence);
        evaluateArtifacts(request, candidate, failures, evidence);
        evaluateExecutionIntents(request, candidate, failures, evidence);
        evaluateAdditionalConstraints(request, candidate, failures, evidence);

        if (candidate.staticCompatibility().loweringSupport() == LoweringSupport.UNSUPPORTED) {
            add(failures, evidence,
                    StaticCompatibilityFailure.LOWERING_SEMANTICALLY_UNREPRESENTABLE,
                    ReferenceKind.LOWERING,
                    unitId.value());
        }

        if (failures.isEmpty()) {
            KernelProof proof = new KernelProof(request, candidate);
            return CompatibilityDecision.kernelCompatible(request, candidate, proof);
        }
        return CompatibilityDecision.incompatible(
                request, candidate, List.copyOf(failures), evidence);
    }

    /** The sole permitted proof implementation; its constructor is inaccessible to callers. */
    static final class KernelProof implements StaticProviderCompatibilityProof {
        private final CompatibilityRequest compatibilityRequest;
        private final ProviderCandidate providerCandidate;

        private KernelProof(
                CompatibilityRequest compatibilityRequest,
                ProviderCandidate providerCandidate) {
            this.compatibilityRequest = compatibilityRequest;
            this.providerCandidate = providerCandidate;
        }

        @Override
        public CompatibilityRequest compatibilityRequest() {
            return compatibilityRequest;
        }

        @Override
        public ProviderCandidate providerCandidate() {
            return providerCandidate;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof KernelProof that
                    && compatibilityRequest.equals(that.compatibilityRequest)
                    && providerCandidate.equals(that.providerCandidate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(compatibilityRequest, providerCandidate);
        }
    }

    private static void evaluateProviderContract(
            ProviderCandidate candidate,
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence) {
        var binding = candidate.bindingPin();
        var descriptor = candidate.descriptor();
        var contract = candidate.executionContract();
        var profile = candidate.capabilityProfile();

        boolean bindingMatchesDescriptor = binding.providerId().equals(descriptor.providerId())
                && binding.providerImplementationId().equals(descriptor.providerImplementationId())
                && binding.providerVersion().equals(descriptor.providerVersion())
                && binding.providerExecutionContractVersion()
                        .equals(descriptor.providerExecutionContractVersion())
                && binding.providerCapabilityProfileVersionOrDigest()
                        .equals(descriptor.providerCapabilityProfileReference());
        boolean descriptorMatchesDeclarations = descriptor.providerExecutionContractVersion()
                        .equals(contract.contractVersion())
                && descriptor.providerCapabilityProfileReference().equals(profile.reference());

        boolean everySupportBackedByContract = profile.supportDeclarations().stream()
                .allMatch(support -> contract.capabilityContractReferences().stream()
                        .anyMatch(reference -> sameCapabilityAndOverlappingRange(support, reference)));
        boolean everyBindingPinDeclared = binding.capabilityImplementationPins().stream()
                .allMatch(pin -> profile.supportDeclarations().stream()
                        .anyMatch(support -> support.capabilityImplementationPin()
                                .map(pin::equals).orElse(false)));

        if (!bindingMatchesDescriptor || !descriptorMatchesDeclarations
                || !everySupportBackedByContract || !everyBindingPinDeclared) {
            add(failures, evidence,
                    StaticCompatibilityFailure.PROVIDER_CONTRACT_INCOMPATIBLE,
                    ReferenceKind.PROVIDER_CONTRACT,
                    binding.providerImplementationId().value());
        }
    }

    private static void evaluateCapabilities(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence) {
        for (var reference : request.physicalPlanUnit().capabilityRequirementRefs()) {
            CapabilityRequirement requirement = reference.declaration();
            if (!requirement.required()) {
                continue;
            }
            List<CapabilityId> acceptableIds = new ArrayList<>();
            acceptableIds.add(requirement.capabilityId());
            acceptableIds.addAll(requirement.alternatives());
            acceptableIds.sort(java.util.Comparator.comparing(CapabilityId::value));

            List<ProviderCapabilitySupport> identityMatches = candidate.capabilityProfile()
                    .supportDeclarations().stream()
                    .filter(support -> acceptableIds.contains(support.capabilityId()))
                    .toList();
            if (identityMatches.isEmpty()) {
                add(failures, evidence,
                        StaticCompatibilityFailure.CAPABILITY_UNSUPPORTED,
                        ReferenceKind.CAPABILITY,
                        capabilityReference(requirement));
                continue;
            }

            List<ProviderCapabilitySupport> versionMatches = identityMatches.stream()
                    .filter(support -> rangesOverlap(
                            requirement.contractRange(), support.contractVersionRange()))
                    .toList();
            if (versionMatches.isEmpty()) {
                add(failures, evidence,
                        StaticCompatibilityFailure.CONTRACT_VERSION_UNSUPPORTED,
                        ReferenceKind.CAPABILITY,
                        capabilityReference(requirement));
                continue;
            }

            boolean contractMatch = versionMatches.stream().anyMatch(support ->
                    candidate.executionContract().capabilityContractReferences().stream()
                            .anyMatch(contractReference -> sameCapabilityAndOverlappingRange(
                                    support, contractReference)
                                    && rangesOverlap(
                                            requirement.contractRange(),
                                            contractReference.contractVersionRange())));
            boolean bindingPinMatch = versionMatches.stream().anyMatch(support ->
                    support.capabilityImplementationPin().isEmpty()
                            || candidate.bindingPin().capabilityImplementationPins()
                                    .contains(support.capabilityImplementationPin().orElseThrow()));
            if (!contractMatch || !bindingPinMatch) {
                add(failures, evidence,
                        StaticCompatibilityFailure.PROVIDER_CONTRACT_INCOMPATIBLE,
                        ReferenceKind.PROVIDER_CONTRACT,
                        capabilityReference(requirement));
            }
        }
    }

    private static void evaluateArtifacts(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence) {
        List<ArtifactRequirementKind> required = new ArrayList<>();
        request.physicalPlanUnit().typedInputs().stream()
                .filter(input -> input.sourceArtifact() != null)
                .forEach(ignored -> addIfAbsent(required, ArtifactRequirementKind.PINNED_SOURCE_INPUT));
        request.physicalPlanUnit().typedOutputs().forEach(output -> {
            if (!output.materializationRequirements().isEmpty()) {
                addIfAbsent(required, ArtifactRequirementKind.MANDATORY_MATERIALIZATION);
            }
            if (!output.intermediateArtifactExpectations().isEmpty()) {
                addIfAbsent(required, ArtifactRequirementKind.INTERMEDIATE_OUTPUT);
            }
            if (!output.finalArtifactExpectations().isEmpty()) {
                addIfAbsent(required, ArtifactRequirementKind.FINAL_OUTPUT);
            }
        });
        required.sort(java.util.Comparator.naturalOrder());

        for (ArtifactRequirementKind artifactRequirement : required) {
            if (!candidate.staticCompatibility().supportedArtifactRequirements()
                    .contains(artifactRequirement)) {
                add(failures, evidence,
                        StaticCompatibilityFailure.ARTIFACT_INCOMPATIBLE,
                        ReferenceKind.ARTIFACT,
                        artifactRequirement.name());
            }
        }
    }

    private static void evaluateExecutionIntents(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence) {
        for (var reference : request.physicalPlanUnit().executionIntentRefs()) {
            RenderExecutionRequirement intent = reference.declaration();
            SandboxMode requiredSandbox = intent.sandboxedIntent()
                    ? SandboxMode.SANDBOXED : SandboxMode.UNSANDBOXED;
            if (!candidate.staticCompatibility().supportedSandboxModes().contains(requiredSandbox)) {
                add(failures, evidence,
                        StaticCompatibilityFailure.SANDBOX_MODE_UNSUPPORTED,
                        ReferenceKind.SANDBOX_MODE,
                        requiredSandbox.name());
            }
            if (!candidate.staticCompatibility().supportedDeterminismClasses()
                    .contains(intent.determinism())) {
                add(failures, evidence,
                        StaticCompatibilityFailure.DETERMINISM_UNSUPPORTED,
                        ReferenceKind.DETERMINISM,
                        intent.determinism().name());
            }
        }
    }

    private static void evaluateAdditionalConstraints(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence) {
        for (StaticCompatibilityConstraint constraint : request.additionalConstraints()) {
            if (constraint instanceof Codec codec) {
                if (!candidate.staticCompatibility().supportedCodecs().contains(codec.codecId())) {
                    add(failures, evidence,
                            StaticCompatibilityFailure.CODEC_UNSUPPORTED,
                            ReferenceKind.CODEC,
                            codec.codecId().value());
                }
            } else if (constraint instanceof StaticCompatibilityConstraint.DeviceKind deviceKind) {
                if (!candidate.staticCompatibility().supportedDeviceKinds()
                        .contains(deviceKind.deviceKind())) {
                    add(failures, evidence,
                            StaticCompatibilityFailure.DEVICE_KIND_UNSUPPORTED_IN_PRINCIPLE,
                            ReferenceKind.DEVICE_KIND,
                            deviceKind.deviceKind().name());
                }
            } else if (constraint instanceof ProviderRuntime runtime) {
                if (!candidate.staticCompatibility().supportedRuntimeClasses()
                        .contains(runtime.runtimeClass())) {
                    add(failures, evidence,
                            StaticCompatibilityFailure.PROVIDER_RUNTIME_CLASS_UNSUPPORTED,
                            ReferenceKind.PROVIDER_RUNTIME_CLASS,
                            runtime.runtimeClass().name());
                }
            } else if (constraint instanceof CrossProviderBoundary boundary
                    && !boundary.upstreamProviderId().equals(candidate.bindingPin().providerId())
                    && !candidate.staticCompatibility().supportedBoundaryContracts()
                            .contains(boundary.boundaryContractId())) {
                add(failures, evidence,
                        StaticCompatibilityFailure.CROSS_PROVIDER_BOUNDARY_INCOMPATIBLE,
                        ReferenceKind.CROSS_PROVIDER_BOUNDARY,
                        boundary.canonicalKey());
            }
        }
    }

    private static boolean sameCapabilityAndOverlappingRange(
            ProviderCapabilitySupport support,
            ProviderCapabilityContractReference reference) {
        return support.capabilityId().equals(reference.capabilityId())
                && rangesOverlap(support.contractVersionRange(), reference.contractVersionRange());
    }

    private static boolean rangesOverlap(ContractVersionRange first, ContractVersionRange second) {
        if (first.min().major() != second.min().major()) {
            return false;
        }
        return compare(first.min(), second.max()) <= 0 && compare(second.min(), first.max()) <= 0;
    }

    private static int compare(ContractVersion first, ContractVersion second) {
        return first.compareTo(second);
    }

    private static String capabilityReference(CapabilityRequirement requirement) {
        ContractVersionRange range = requirement.contractRange();
        return requirement.capabilityId().value() + "@"
                + range.min().major() + "." + range.min().minor() + "-"
                + range.max().major() + "." + range.max().minor();
    }

    private static void add(
            EnumSet<StaticCompatibilityFailure> failures,
            List<CompatibilityEvidence> evidence,
            StaticCompatibilityFailure failure,
            ReferenceKind referenceKind,
            String canonicalReference) {
        failures.add(failure);
        CompatibilityEvidence item = evidence(failure, referenceKind, canonicalReference);
        if (!evidence.contains(item)) {
            evidence.add(item);
        }
    }

    private static CompatibilityEvidence evidence(
            StaticCompatibilityFailure failure,
            ReferenceKind referenceKind,
            String canonicalReference) {
        return new CompatibilityEvidence(failure, referenceKind, canonicalReference);
    }

    private static <T> void addIfAbsent(List<T> values, T value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
