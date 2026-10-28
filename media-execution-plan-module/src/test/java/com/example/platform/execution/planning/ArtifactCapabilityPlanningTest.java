package com.example.platform.execution.planning;

import com.example.platform.execution.binding.*;
import com.example.platform.execution.compatibility.*;
import com.example.platform.execution.domain.*;
import com.example.platform.execution.domain.provider.*;
import com.example.platform.render.domain.renderplan.*;
import com.example.platform.shared.capability.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactCapabilityPlanningTest {
    ArtifactCapabilityRequest request(String scope, String source, String digest, String time, String capability) {
        return new ArtifactCapabilityRequest(scope, capability,
                new com.example.platform.artifact.app.ArtifactPinService.ArtifactPin(new ArtifactId(source), ContentDigest.sha256(digest)),
                MediaFrameExtractParametersV1.ofExactSeconds(time, RasterImageEncoding.Jpeg.documentedDefault(), new FrameWidth.ExplicitPixels(160)));
    }
    PhysicalExecutionPlan plan(ArtifactCapabilityRequest request) {
        return new PhysicalPlanningEntryService().planArtifactCapability(request, new ExecutionPlanId("opaque-plan-identity"));
    }
    ProviderCandidate candidate(String provider, String version) {
        var id=ProviderId.of(provider);var implementation=ProviderImplementationId.of(provider+".impl");
        var v=ProviderVersion.of(version);var contract=ProviderExecutionContractVersion.of(1,0);
        var profile=ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1,0));
        var pin=new ProviderBindingPin(id,implementation,v,contract,profile,List.of());
        var range=ContractVersionRange.exactly(ContractVersion.of(1,0));
        var cover=CapabilityId.of("media.cover-image");var thumb=CapabilityId.of("media.thumbnail");
        return new ProviderCandidate(pin,new ProviderDescriptor(id,implementation,v,contract,profile),
                new ProviderExecutionContract(ProviderExecutionContractSchemaVersion.of(1),contract,List.of(
                        new ProviderCapabilityContractReference(cover,range),new ProviderCapabilityContractReference(thumb,range))),
                new ProviderCapabilityProfile(profile,List.of(ProviderCapabilitySupport.unpinned(cover,range),ProviderCapabilitySupport.unpinned(thumb,range))),
                new ProviderStaticCompatibility(ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                        List.of(),List.of(StaticCompatibilityConstraint.ProviderDeviceKind.CPU),
                        List.of(StaticCompatibilityConstraint.ProviderRuntimeClass.NATIVE_PROCESS,StaticCompatibilityConstraint.ProviderRuntimeClass.ISOLATED_PROCESS),
                        List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                        List.of(),ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
    @Test void typedArtifactRequestUsesExistingPlanningBindingAndMaterializationWithoutRenderJob() {
        var request=request("1".repeat(64),"source-a","a".repeat(64),"0.25","media.cover-image");
        var plan=plan(request);
        var rebound=PhysicalExecutionPlanCanonicalCodec.decode(PhysicalExecutionPlanCanonicalCodec.encode(plan));
        assertEquals(plan,rebound);
        var graph=new ProviderBindingEntryService().bind(plan,List.of(candidate("ffmpeg","1.0.0")),List.of()).executableTaskGraph();
        assertTrue(plan.units().getFirst().executionIntentRefs().isEmpty());
        assertEquals(1,graph.tasks().size());
        assertEquals(1,graph.tasks().getFirst().authoritativeOutputIds().size());
        assertEquals(request,graph.tasks().getFirst().memberships().getFirst().physicalPlanUnit().artifactCapabilityRequest());
        assertEquals(RenderOutputRole.DERIVED_MEDIA,plan.units().getFirst().typedOutputs().getFirst().finalArtifactExpectations().getFirst().role());
        assertEquals(85,((RasterImageEncoding.Jpeg)request.parameters().encoding()).quality().orElseThrow().value());
        assertFalse(plan.toString().contains("RENDER_MASTER"));
    }
    @Test void scopeInputContentPositionAndCapabilityAreAllFingerprintInputs() {
        var base=request("1".repeat(64),"source-a","a".repeat(64),"0.25","media.cover-image");
        var variants=List.of(
                request("2".repeat(64),"source-a","a".repeat(64),"0.25","media.cover-image"),
                request("1".repeat(64),"source-b","a".repeat(64),"0.25","media.cover-image"),
                request("1".repeat(64),"source-a","b".repeat(64),"0.25","media.cover-image"),
                request("1".repeat(64),"source-a","a".repeat(64),"0.5","media.cover-image"),
                request("1".repeat(64),"source-a","a".repeat(64),"0.25","media.thumbnail"));
        for(var changed:variants)assertNotEquals(plan(base).digest(),plan(changed).digest());
        assertEquals(plan(base).digest(),plan(request("1".repeat(64),"source-a","a".repeat(64),"0.2500","media.cover-image")).digest());
    }
    @Test void bindingRejectsNoCandidateAmbiguityAndForgedIdentityThroughActualKernel() {
        var plan=plan(request("1".repeat(64),"source-a","a".repeat(64),"0","media.thumbnail"));
        var binder=new ProviderBindingEntryService();var first=candidate("ffmpeg","1.0.0");
        assertThrows(ProviderBindingException.class,()->binder.bind(plan,List.of(),List.of()));
        assertThrows(ProviderBindingException.class,()->binder.bind(plan,List.of(first,candidate("other","1.0.0")),List.of()));
        var other=candidate("other","1.0.0");
        var forged=new ProviderCandidate(first.bindingPin(),other.descriptor(),first.executionContract(),first.capabilityProfile(),first.staticCompatibility());
        assertThrows(ProviderBindingException.class,()->binder.bind(plan,List.of(forged),List.of()));
        var old=binder.bind(plan,List.of(first),List.of()).executableTaskGraph();
        var updated=binder.bind(plan,List.of(candidate("ffmpeg","1.0.1")),List.of()).executableTaskGraph();
        assertNotEquals(old.digest(),updated.digest());
        assertNotEquals(old.tasks().getFirst().providerBindingPin(),updated.tasks().getFirst().providerBindingPin());
    }
}
