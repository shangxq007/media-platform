package com.example.platform.studio.camera;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;
public record Transform(Vector3 translationMeters,Quaternion rotation,Vector3 scale){public Transform{if(translationMeters==null||rotation==null||scale==null)throw new IllegalArgumentException("transform fields required");if(!scale.x().isPositive()||!scale.y().isPositive()||!scale.z().isPositive())throw new IllegalArgumentException("scale must be positive");}
public String canonicalJson(){return CanonicalJson.object(Map.of("rotation",rotation.canonicalJson(),"scale",scale.canonicalJson(),"translationMeters",translationMeters.canonicalJson()));}}
