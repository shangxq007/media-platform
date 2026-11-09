package com.example.platform.config.firstrender;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.semantics.temporal.*;
import com.example.platform.timeline.semantics.effect.*;
import com.example.platform.timeline.version.*;
import com.example.platform.render.domain.renderplan.*;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.execution.planning.*;
import com.example.platform.execution.binding.*;
import com.example.platform.execution.taskgraph.*;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.shared.time.*;
import com.example.platform.shared.identity.ArtifactId;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full production #20/#21/#22 chain, not hand-built task identities. */
class IncrementalExecutionImpactTest {
    static final EffectSemanticSnapshot EFFECT=new EffectSemanticSnapshotAuthority(new EffectDefinitionVersionRegistry.InMemory(),new EffectSemanticSnapshotStore.InMemory()).mintEmpty();
    static TimelineClip clip(String id,long start,long end,String digest) {
        return new TimelineClip(id,"asset-"+id,"stream-"+id,"source-"+id,digest,
            MediaTime.ofMillis(0),MediaTime.ofMillis(end-start),MediaTime.ofMillis(start),MediaTime.ofMillis(end),"MEDIA_STREAM",ConstantRateTemporalMapping.of(1,1,PlaybackDirection.FORWARD));
    }
    static ProviderBoundExecutableTaskGraph graph(List<TimelineClip> clips) {
        var document=new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,List.of(new TimelineTrack("track","v",TrackType.VIDEO,clips)),TimelineMetadata.empty(),AudioMix.EMPTY,List.of(),List.of());
        String digest=new TimelineContentDigester().digest(document);
        String semantic=TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(digest,EFFECT.reference());
        var revision=new TimelineRevision(UUID.randomUUID().toString(),"project",null,TimelineDocument.CURRENT_SCHEMA_VERSION,null,semantic,Instant.EPOCH,"test",
            new TimelineRevisionSemanticContext(digest,EFFECT.reference(),semantic,TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1)).hydrate(document);
        Map<ArtifactId,RenderSourceResolutionState> sources=new HashMap<>();clips.forEach(c->sources.put(new ArtifactId("source-"+c.clipId()),RenderSourceResolutionState.RESOLVED));
        var result=new RenderPlanningEntryService().plan(revision,EFFECT,
            new RenderRequest(new RenderRequestId(UUID.randomUUID().toString()),new RenderExtent(MediaTime.ofMillis(0),MediaTime.ofMillis(3000),FrameRate.of(4,1)),List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER))),
            new SourceResolutionInput(sources),new CapabilityContext(Set.of(RenderCapabilityVocabulary.videoDecode().capabilityId(),RenderCapabilityVocabulary.composite().capabilityId(),RenderCapabilityVocabulary.outputEncode().capabilityId())));
        var physical=new PhysicalPlanningEntryService().plan(result,new ExecutionPlanId(UUID.randomUUID().toString())).physicalExecutionPlan();
        return new ProviderBindingEntryService().bind(physical,ProviderCandidateProjection.project(FirstRenderPlanningFixture.catalog()),List.of()).executableTaskGraph();
    }
    List<TimelineClip> base() {return List.of(clip("red",0,500,"a".repeat(64)),clip("blue",1000,1500,"b".repeat(64)),clip("green",2500,3000,"c".repeat(64)));}
    @Test void unchangedRevisionKeepsActualKeysButNeverBorrowsFinalOwnership() {
        var impact=IncrementalExecutionImpact.analyze(graph(base()),graph(base()));
        assertEquals(4,impact.decisions().values().stream().filter(IncrementalExecutionImpact.Decision::unchanged).count());
        assertTrue(impact.affectedRegions().isEmpty());
    }
    @Test void trimAndMoveInvalidateTheirDecodeAndItsConsumersButKeepSiblings() {
        for(var blue:List.of(clip("blue",1000,1250,"b".repeat(64)),clip("blue",1500,2000,"b".repeat(64)))) {
            var changed=List.of(base().get(0),blue,base().get(2));var impact=IncrementalExecutionImpact.analyze(graph(changed),graph(base()));
            assertEquals(2,impact.decisions().values().stream().filter(IncrementalExecutionImpact.Decision::unchanged).count());
            assertFalse(impact.affectedRegions().isEmpty());
        }
    }
    @Test void deleteKeepsSurvivingMultiClipDecodeComputations() {
        var impact=IncrementalExecutionImpact.analyze(graph(List.of(base().get(0),base().get(2))),graph(base()));
        assertEquals(2,impact.decisions().values().stream().filter(IncrementalExecutionImpact.Decision::unchanged).count());
        assertFalse(impact.affectedRegions().isEmpty());
    }
    @Test void changedInputDigestCannotBorrowOldDecode() {
        var impact=IncrementalExecutionImpact.analyze(graph(List.of(base().get(0),base().get(1),clip("green",2500,3000,"d".repeat(64)))),graph(base()));
        assertEquals(2,impact.decisions().values().stream().filter(IncrementalExecutionImpact.Decision::unchanged).count());
    }
}
