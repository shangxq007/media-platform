package com.example.platform.studio.camera;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;
public sealed interface CameraOptics permits CameraOptics.FocalLengthMm,CameraOptics.FieldOfViewDegrees{String canonicalJson();
record FocalLengthMm(DecimalValue value)implements CameraOptics{public FocalLengthMm{if(value==null||value.compareTo(DecimalValue.of("1"))<0||value.compareTo(DecimalValue.of("1000"))>0)throw new IllegalArgumentException("focal length range invalid");}public String canonicalJson(){return CanonicalJson.object(Map.of("type",CanonicalJson.quote("FOCAL_LENGTH_MM"),"value",CanonicalJson.quote(value.canonical())));}}
record FieldOfViewDegrees(DecimalValue value)implements CameraOptics{public FieldOfViewDegrees{if(value==null||value.compareTo(DecimalValue.of("0"))<=0||value.compareTo(DecimalValue.of("180"))>=0)throw new IllegalArgumentException("field of view range invalid");}public String canonicalJson(){return CanonicalJson.object(Map.of("type",CanonicalJson.quote("FIELD_OF_VIEW_DEGREES"),"value",CanonicalJson.quote(value.canonical())));}}
}
