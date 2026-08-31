package com.example.platform.studio.camera;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;
public record Vector3(DecimalValue x,DecimalValue y,DecimalValue z){
    public static final Vector3 ZERO=new Vector3(DecimalValue.of("0"),DecimalValue.of("0"),DecimalValue.of("0"));
    public static final Vector3 ONE=new Vector3(DecimalValue.of("1"),DecimalValue.of("1"),DecimalValue.of("1"));
    public Vector3{if(x==null||y==null||z==null)throw new IllegalArgumentException("finite vector components required");}
    public String canonicalJson(){return CanonicalJson.object(Map.of("x",CanonicalJson.quote(x.canonical()),"y",CanonicalJson.quote(y.canonical()),"z",CanonicalJson.quote(z.canonical())));}
}
