package com.example.platform.studio.shotplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.digest.StudioDigest;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.screenplay.ScreenplayVersion;
import com.example.platform.studio.serialization.CanonicalJson;
import com.example.platform.studio.shot.ShotVersion;
import java.util.*;

public final class ShotPlanVersion implements com.example.platform.studio.digest.CanonicalStudioVersion {
    public static final int SCHEMA_VERSION=1;
    private final ShotPlanId id; private final ShotPlanVersionId versionId; private final StudioScope scope;
    private final ShotPlanVersionId parentVersionId; private final StudioVersionPin<ScreenplayId,ScreenplayVersionId> screenplayPin;
    private final StudioVersionPin<SceneId,SceneVersionId> scenePin; private final List<StudioVersionPin<ShotId,ShotVersionId>> shotPins;
    private final byte[] canonicalBytes; private final ContentDigest semanticDigest;
    private ShotPlanVersion(ShotPlanId id,ShotPlanVersionId versionId,StudioScope scope,ShotPlanVersionId parentVersionId,
            ScreenplayVersion screenplay,SceneVersion scene,List<ShotVersion> shots){
        if(id==null||versionId==null||scope==null||screenplay==null||scene==null||shots==null||shots.isEmpty())throw new IllegalArgumentException("shot plan fields are required");
        if(versionId.equals(parentVersionId))throw new IllegalArgumentException("version cannot parent itself");
        if(!scope.equals(screenplay.scope())||!scope.equals(scene.scope()))throw new IllegalArgumentException("scope mismatch");
        if(!scene.screenplayPin().equals(screenplay.pin()))throw new IllegalArgumentException("screenplay/scene lineage mismatch");
        var pins=new ArrayList<StudioVersionPin<ShotId,ShotVersionId>>();var seen=new HashSet<StudioVersionPin<ShotId,ShotVersionId>>();
        for(var shot:shots){if(shot==null||!shot.scope().equals(scope)||!shot.scenePin().equals(scene.pin()))throw new IllegalArgumentException("shot scene lineage mismatch");
            var pin=shot.pin();if(!seen.add(pin))throw new IllegalArgumentException("duplicate exact shot pin");pins.add(pin);}
        this.id=id;this.versionId=versionId;this.scope=scope;this.parentVersionId=parentVersionId;this.screenplayPin=screenplay.pin();this.scenePin=scene.pin();this.shotPins=List.copyOf(pins);
        this.canonicalBytes=serialize();this.semanticDigest=StudioDigest.sha256(canonicalBytes);
    }
    public static ShotPlanVersion create(ShotPlanId id,ShotPlanVersionId versionId,StudioScope scope,ShotPlanVersionId parentVersionId,
            ScreenplayVersion screenplay,SceneVersion scene,List<ShotVersion> shots){return new ShotPlanVersion(id,versionId,scope,parentVersionId,screenplay,scene,shots);}
    private byte[] serialize(){Map<String,String>m=new LinkedHashMap<>();m.put("aggregateId",CanonicalJson.quote(id.value()));if(parentVersionId!=null)m.put("parentVersionId",CanonicalJson.quote(parentVersionId.value()));
        m.put("projectId",CanonicalJson.quote(scope.projectId().value()));m.put("scenePin",scenePin.canonicalJson());m.put("schemaVersion","1");
        m.put("screenplayPin",screenplayPin.canonicalJson());m.put("shotPins",CanonicalJson.array(shotPins.stream().map(StudioVersionPin::canonicalJson).toList()));
        m.put("tenantId",CanonicalJson.quote(scope.tenantId().value()));m.put("type",CanonicalJson.quote("SHOT_PLAN_VERSION"));m.put("versionId",CanonicalJson.quote(versionId.value()));return CanonicalJson.utf8(CanonicalJson.object(m));}
    public StudioVersionPin<ShotPlanId,ShotPlanVersionId> pin(){return new StudioVersionPin<>(StudioVersionPin.AggregateKind.SHOT_PLAN,id,versionId,scope,1,semanticDigest);}
    public ShotPlanId id(){return id;}public ShotPlanVersionId versionId(){return versionId;}public StudioScope scope(){return scope;}public ShotPlanVersionId parentVersionId(){return parentVersionId;}
    public StudioVersionPin<ScreenplayId,ScreenplayVersionId> screenplayPin(){return screenplayPin;}public StudioVersionPin<SceneId,SceneVersionId> scenePin(){return scenePin;}
    public List<StudioVersionPin<ShotId,ShotVersionId>> shotPins(){return shotPins;}public byte[] canonicalBytes(){return canonicalBytes.clone();}public ContentDigest semanticDigest(){return semanticDigest;}
}
