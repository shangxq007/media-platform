package com.example.platform.studio.storyboard;
import com.example.platform.shared.digest.ContentDigest;import com.example.platform.shared.identity.ArtifactId;import com.example.platform.studio.serialization.CanonicalJson;import java.util.Map;
public sealed interface PanelImage permits PanelImage.Planned,PanelImage.Materialized{String canonicalJson();
record Planned()implements PanelImage{public String canonicalJson(){return CanonicalJson.object(Map.of("type",CanonicalJson.quote("PLANNED_UNMATERIALIZED")));}}
record Materialized(ArtifactId artifactId,ContentDigest contentDigest)implements PanelImage{public Materialized{if(artifactId==null||contentDigest==null)throw new IllegalArgumentException("materialized image requires artifact and content digest pair");}public String canonicalJson(){return CanonicalJson.object(Map.of("artifactId",CanonicalJson.quote(artifactId.value()),"contentDigest",CanonicalJson.quote(contentDigest.canonicalValue()),"type",CanonicalJson.quote("MATERIALIZED")));}}
}
