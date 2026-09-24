package com.example.platform.composition.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Adapter over existing artifact and entitlement authorities; it owns no resource state. */
@Component
public class CompositionResourceAuthority {
    public record ResourceCheck(Set<String> availableAssets, Set<String> grantedEntitlements, List<CompositionModelsIssue> issues) {}
    public record CompositionModelsIssue(String code, String path, String message) {}
    private final ArtifactQueryService artifacts;
    private final EntitlementDecisionQuery entitlements;
    public CompositionResourceAuthority(ArtifactQueryService artifacts, EntitlementDecisionQuery entitlements) { this.artifacts=artifacts; this.entitlements=entitlements; }

    public ResourceCheck resolve(String tenant,String workspace,String subject,String compositionId,Set<String> assetRequirements,Set<String> entitlementRequirements,BigDecimal quota) {
        List<CompositionModelsIssue> issues=new ArrayList<>(); Set<String> available=new HashSet<>();
        for(String requirement: assetRequirements==null?Set.<String>of():assetRequirements) {
            String[] parts=requirement.split("\\|",-1); String id=parts.length==3?parts[2]:requirement;
            Artifact artifact;
            try { artifact=artifacts.getArtifact(tenant,new com.example.platform.shared.identity.ArtifactId(id)).orElse(null); }
            catch(RuntimeException e) { artifact=null; }
            if(artifact==null) { issues.add(new CompositionModelsIssue("ASSET_INACCESSIBLE","requiredAssets."+requirement,"asset is missing or inaccessible")); continue; }
            if(parts.length==3) {
                try { if(!ArtifactKind.valueOf(parts[0]).equals(artifact.artifactKind())) { issues.add(new CompositionModelsIssue("ASSET_KIND_UNSUPPORTED","requiredAssets."+requirement,"asset kind does not match")); continue; }
                    if(!ArtifactMediaType.valueOf(parts[1]).equals(artifact.mediaType())) { issues.add(new CompositionModelsIssue("ASSET_MEDIA_UNSUPPORTED","requiredAssets."+requirement,"asset media type does not match")); continue; }
                } catch(IllegalArgumentException e) { issues.add(new CompositionModelsIssue("MALFORMED_ASSET_REQUIREMENT","requiredAssets."+requirement,"asset requirement must be KIND|MEDIA|ID")); continue; }
            }
            available.add(requirement);
        }
        Set<String> granted=new HashSet<>();
        for(String entitlement: entitlementRequirements==null?Set.<String>of():entitlementRequirements) {
            var decision=entitlements.evaluate(new AccessCheckRequest(tenant,workspace,subject,"USER",subject,"composition.publish","COMPOSITION",compositionId,entitlement,null,null,"WEB",quota,Map.of()));
            if(decision.allowed()) granted.add(entitlement); else issues.add(new CompositionModelsIssue("MISSING_ENTITLEMENT","entitlements."+entitlement,"entitlement is missing or quota is insufficient"));
        }
        return new ResourceCheck(Set.copyOf(available),Set.copyOf(granted),List.copyOf(issues));
    }
}
