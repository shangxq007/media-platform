package com.example.platform.studio.directorintent;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.digest.StudioDigest;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.*;
import java.util.regex.Pattern;

public final class DirectorIntentVersion implements com.example.platform.studio.digest.CanonicalStudioVersion {
    public static final int SCHEMA_VERSION=1;
    private static final Pattern EXECUTABLE=Pattern.compile("(?i)(^|\\s)(exec|execute|script|shell|command|curl|wget|sudo|render)(\\s|$)|--|://|[{};$|`]");
    public enum Emphasis { SUBJECT, RELATIONSHIP, ENVIRONMENT, REVEAL }
    public enum EmotionalTone { CALM, TENSE, JOYFUL, SOMBER, UNCERTAIN }
    public enum CameraMovementIntent { STATIC, PAN, TILT, PUSH_IN, PULL_BACK, TRACK, ORBIT }
    public enum LightingMood { NATURAL, HIGH_KEY, LOW_KEY, SILHOUETTE, DIFFUSE }
    private final DirectorIntentId id;private final DirectorIntentVersionId versionId;private final StudioScope scope;
    private final DirectorIntentVersionId parentVersionId;private final Emphasis emphasis;private final EmotionalTone emotionalTone;
    private final CameraMovementIntent movementIntent;private final LightingMood lightingMood;private final List<String> annotations;
    private final byte[] canonicalBytes;private final ContentDigest semanticDigest;
    private DirectorIntentVersion(DirectorIntentId id,DirectorIntentVersionId versionId,StudioScope scope,DirectorIntentVersionId parentVersionId,
            Emphasis emphasis,EmotionalTone emotionalTone,CameraMovementIntent movementIntent,LightingMood lightingMood,List<String> annotations){
        if(id==null||versionId==null||scope==null||emphasis==null||emotionalTone==null||movementIntent==null||lightingMood==null||annotations==null)
            throw new IllegalArgumentException("director intent fields are required");if(versionId.equals(parentVersionId))throw new IllegalArgumentException("version cannot parent itself");
        var safe=new ArrayList<String>();for(var text:annotations){text=CanonicalJson.requiredText(text,"annotation");if(text.length()>500||EXECUTABLE.matcher(text).find())throw new IllegalArgumentException("annotation violates safe semantic contract");safe.add(text);}
        this.id=id;this.versionId=versionId;this.scope=scope;this.parentVersionId=parentVersionId;this.emphasis=emphasis;this.emotionalTone=emotionalTone;
        this.movementIntent=movementIntent;this.lightingMood=lightingMood;this.annotations=List.copyOf(safe);this.canonicalBytes=serialize();this.semanticDigest=StudioDigest.sha256(canonicalBytes);
    }
    public static DirectorIntentVersion create(DirectorIntentId id,DirectorIntentVersionId versionId,StudioScope scope,DirectorIntentVersionId parentVersionId,
            Emphasis emphasis,EmotionalTone emotionalTone,CameraMovementIntent movementIntent,LightingMood lightingMood,List<String> annotations){return new DirectorIntentVersion(id,versionId,scope,parentVersionId,emphasis,emotionalTone,movementIntent,lightingMood,annotations);}
    private byte[] serialize(){Map<String,String>m=new LinkedHashMap<>();m.put("aggregateId",CanonicalJson.quote(id.value()));m.put("annotations",CanonicalJson.array(annotations.stream().map(CanonicalJson::quote).toList()));m.put("cameraMovementIntent",CanonicalJson.quote(movementIntent.name()));m.put("emotionalTone",CanonicalJson.quote(emotionalTone.name()));m.put("emphasis",CanonicalJson.quote(emphasis.name()));m.put("lightingMood",CanonicalJson.quote(lightingMood.name()));if(parentVersionId!=null)m.put("parentVersionId",CanonicalJson.quote(parentVersionId.value()));m.put("projectId",CanonicalJson.quote(scope.projectId().value()));m.put("schemaVersion","1");m.put("tenantId",CanonicalJson.quote(scope.tenantId().value()));m.put("type",CanonicalJson.quote("DIRECTOR_INTENT_VERSION"));m.put("versionId",CanonicalJson.quote(versionId.value()));return CanonicalJson.utf8(CanonicalJson.object(m));}
    public StudioVersionPin<DirectorIntentId,DirectorIntentVersionId> pin(){return new StudioVersionPin<>(StudioVersionPin.AggregateKind.DIRECTOR_INTENT,id,versionId,scope,1,semanticDigest);}
    public DirectorIntentId id(){return id;}public DirectorIntentVersionId versionId(){return versionId;}public StudioScope scope(){return scope;}public DirectorIntentVersionId parentVersionId(){return parentVersionId;}
    public byte[] canonicalBytes(){return canonicalBytes.clone();}public ContentDigest semanticDigest(){return semanticDigest;}public List<String> annotations(){return annotations;}
}
