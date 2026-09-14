package com.example.platform.render.api.context;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import java.time.Instant;
import java.util.List;
/** Immutable acceptance-time facts, not a perpetual authorization or reservation. */
public record AcceptedExecutionContext(int schemaVersion,String jobId,String actorId,ActorType actorKind,
        String accountId,String tenantMembershipId,String tenantId,String workspaceId,String projectId,
        String consumptionPrincipalType,String consumptionPrincipalId,String allocationMode,String allocationSourceId,
        String entitlementAuthorityVersion,String entitlementGrantId,long entitlementGrantVersion,List<CommercialEvidenceRef> commercialEvidence,String timelineSnapshotId,Instant acceptedAt) {
    public AcceptedExecutionContext {
        commercialEvidence=List.copyOf(commercialEvidence);
        if(schemaVersion!=1||jobId==null||actorId==null||actorKind==null||tenantId==null||workspaceId==null||projectId==null
                ||!"ORGANIZATION".equals(consumptionPrincipalType)||!tenantId.equals(consumptionPrincipalId)
                ||!"TENANT_ORGANIZATION".equals(allocationMode)||allocationSourceId!=null||entitlementGrantId==null||entitlementGrantVersion<0)
            throw new IllegalArgumentException("Invalid accepted context facts");
        if(actorKind==ActorType.USER&&(accountId==null||!actorId.equals(tenantMembershipId)))throw new IllegalArgumentException("Account membership context required");
        if(actorKind!=ActorType.USER&&(accountId!=null||tenantMembershipId!=null))throw new IllegalArgumentException("Nonhuman principal is not an Account membership");
    }
}
