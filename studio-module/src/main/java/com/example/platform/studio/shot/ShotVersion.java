package com.example.platform.studio.shot;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.digest.StudioDigest;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShotVersion implements com.example.platform.studio.digest.CanonicalStudioVersion {
    public static final int SCHEMA_VERSION = 1;
    private final ShotId id; private final ShotVersionId versionId; private final StudioScope scope;
    private final ShotVersionId parentVersionId; private final StudioVersionPin<SceneId, SceneVersionId> scenePin;
    private final String description; private final String subjectActionIntent; private final String continuityIntent;
    private final MediaTime planningDuration; private final StudioVersionPin<?, ?> directorIntentPin;
    private final StudioVersionPin<?, ?> cameraPlanPin; private final byte[] canonicalBytes; private final ContentDigest semanticDigest;

    private ShotVersion(ShotId id, ShotVersionId versionId, StudioScope scope, ShotVersionId parentVersionId,
            SceneVersion scene, String description, String subjectActionIntent, String continuityIntent,
            MediaTime planningDuration, StudioVersionPin<?, ?> directorIntentPin, StudioVersionPin<?, ?> cameraPlanPin) {
        if (id == null || versionId == null || scope == null || scene == null || planningDuration == null)
            throw new IllegalArgumentException("shot version fields are required");
        if (!scope.equals(scene.scope())) throw new IllegalArgumentException("scene scope mismatch");
        if (planningDuration.equals(MediaTime.ZERO)) throw new IllegalArgumentException("planning duration must be positive");
        if (versionId.equals(parentVersionId)) throw new IllegalArgumentException("version cannot parent itself");
        validateOptionalPin(directorIntentPin, StudioVersionPin.AggregateKind.DIRECTOR_INTENT, scope);
        validateOptionalPin(cameraPlanPin, StudioVersionPin.AggregateKind.CAMERA_PLAN, scope);
        this.id=id; this.versionId=versionId; this.scope=scope; this.parentVersionId=parentVersionId; this.scenePin=scene.pin();
        this.description=CanonicalJson.requiredText(description,"description");
        this.subjectActionIntent=CanonicalJson.requiredText(subjectActionIntent,"subject action intent");
        this.continuityIntent=CanonicalJson.requiredText(continuityIntent,"continuity intent"); this.planningDuration=planningDuration;
        this.directorIntentPin=directorIntentPin; this.cameraPlanPin=cameraPlanPin; this.canonicalBytes=serialize();
        this.semanticDigest=StudioDigest.sha256(canonicalBytes);
    }
    public static ShotVersion create(ShotId id, ShotVersionId versionId, StudioScope scope, ShotVersionId parentVersionId,
            SceneVersion scene, String description, String subjectActionIntent, String continuityIntent, MediaTime planningDuration,
            StudioVersionPin<?, ?> directorIntentPin, StudioVersionPin<?, ?> cameraPlanPin) {
        return new ShotVersion(id,versionId,scope,parentVersionId,scene,description,subjectActionIntent,continuityIntent,planningDuration,directorIntentPin,cameraPlanPin);
    }
    private static void validateOptionalPin(StudioVersionPin<?, ?> pin, StudioVersionPin.AggregateKind kind, StudioScope scope) {
        if (pin != null && (pin.kind()!=kind || !pin.scope().equals(scope))) throw new IllegalArgumentException(kind + " pin mismatch");
    }
    private byte[] serialize() {
        Map<String,String> m=new LinkedHashMap<>(); m.put("aggregateId",CanonicalJson.quote(id.value()));
        if(cameraPlanPin!=null)m.put("cameraPlanPin",cameraPlanPin.canonicalJson()); m.put("continuityIntent",CanonicalJson.quote(continuityIntent));
        m.put("description",CanonicalJson.quote(description)); if(directorIntentPin!=null)m.put("directorIntentPin",directorIntentPin.canonicalJson());
        if(parentVersionId!=null)m.put("parentVersionId",CanonicalJson.quote(parentVersionId.value()));
        m.put("planningDuration",CanonicalJson.object(Map.of("ticks",Long.toString(planningDuration.ticks()),"timeScale",Long.toString(planningDuration.timeScale()))));
        m.put("projectId",CanonicalJson.quote(scope.projectId().value()));m.put("scenePin",scenePin.canonicalJson());m.put("schemaVersion","1");
        m.put("subjectActionIntent",CanonicalJson.quote(subjectActionIntent));m.put("tenantId",CanonicalJson.quote(scope.tenantId().value()));
        m.put("type",CanonicalJson.quote("SHOT_VERSION"));m.put("versionId",CanonicalJson.quote(versionId.value()));
        return CanonicalJson.utf8(CanonicalJson.object(m));
    }
    public StudioVersionPin<ShotId,ShotVersionId> pin(){return new StudioVersionPin<>(StudioVersionPin.AggregateKind.SHOT,id,versionId,scope,1,semanticDigest);}
    public ShotId id(){return id;} public ShotVersionId versionId(){return versionId;} public StudioScope scope(){return scope;}
    public ShotVersionId parentVersionId(){return parentVersionId;} public StudioVersionPin<SceneId,SceneVersionId> scenePin(){return scenePin;}
    public MediaTime planningDuration(){return planningDuration;} public byte[] canonicalBytes(){return canonicalBytes.clone();}
    public ContentDigest semanticDigest(){return semanticDigest;} public String description(){return description;}
}
