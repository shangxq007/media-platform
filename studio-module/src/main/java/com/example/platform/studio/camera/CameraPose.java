package com.example.platform.studio.camera;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;
public sealed interface CameraPose permits CameraPose.TransformPose,CameraPose.LookAtPose{String canonicalJson();
record TransformPose(Transform transform)implements CameraPose{public TransformPose{if(transform==null)throw new IllegalArgumentException("transform required");}public String canonicalJson(){return CanonicalJson.object(Map.of("transform",transform.canonicalJson(),"type",CanonicalJson.quote("TRANSFORM")));}}
record LookAtPose(Vector3 positionMeters,Vector3 targetMeters,Vector3 up)implements CameraPose{public LookAtPose{if(positionMeters==null||targetMeters==null||up==null||positionMeters.equals(targetMeters))throw new IllegalArgumentException("valid look-at pose required");}public String canonicalJson(){return CanonicalJson.object(Map.of("positionMeters",positionMeters.canonicalJson(),"targetMeters",targetMeters.canonicalJson(),"type",CanonicalJson.quote("LOOK_AT"),"up",up.canonicalJson()));}}
}
