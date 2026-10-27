package com.example.platform.config.firstrender;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflow;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

/** Test-only immutable provider metadata shared by planning and PG failure-boundary scenarios. */
public final class FirstRenderPlanningFixture {
    private FirstRenderPlanningFixture() {}
    /** A production-shaped provider catalog contribution (mirrors the bounded FFmpeg declarations). */
    public static ProviderPluginCatalog catalog() {
        ContractVersionRange range = ContractVersionRange.exactly(ContractVersion.of(1, 0));
        ProviderId providerId = ProviderId.of("ffmpeg");
        ProviderImplementationId implementationId = ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileRef =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        CapabilityId decode = CapabilityId.of("video.decode");
        CapabilityId composite = CapabilityId.of("render.composite");
        CapabilityId output = CapabilityId.of("render.output");

        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileRef, List.of());
        ProviderDescriptor descriptor =
                new ProviderDescriptor(providerId, implementationId, version, contractVersion, profileRef);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), contractVersion,
                List.of(new ProviderCapabilityContractReference(decode, range),
                        new ProviderCapabilityContractReference(composite, range),
                        new ProviderCapabilityContractReference(output, range)));
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(profileRef,
                List.of(ProviderCapabilitySupport.unpinned(decode, range),
                        ProviderCapabilitySupport.unpinned(composite, range),
                        ProviderCapabilitySupport.unpinned(output, range)));
        ProviderStaticCompatibility compatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                List.of(StaticCompatibilityConstraint.CodecId.of("h264")),
                List.of(StaticCompatibilityConstraint.ProviderDeviceKind.CPU),
                List.of(StaticCompatibilityConstraint.ProviderRuntimeClass.NATIVE_PROCESS,
                        StaticCompatibilityConstraint.ProviderRuntimeClass.ISOLATED_PROCESS),
                List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),
                List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                List.of(),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);

        ProviderPluginContribution contribution = mock(ProviderPluginContribution.class);
        when(contribution.providerBindingPin()).thenReturn(binding);
        when(contribution.providerDescriptor()).thenReturn(descriptor);
        when(contribution.providerExecutionContract()).thenReturn(contract);
        when(contribution.providerCapabilityProfile()).thenReturn(profile);
        when(contribution.providerStaticCompatibility()).thenReturn(compatibility);

        ProviderPluginCatalog catalog = mock(ProviderPluginCatalog.class);
        when(catalog.contributions()).thenReturn(List.of(contribution));
        return catalog;
    }

}
