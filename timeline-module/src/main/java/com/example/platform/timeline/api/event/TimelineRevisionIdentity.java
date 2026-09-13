package com.example.platform.timeline.api.event;
/** Identity of a revision accepted by the Timeline owner. */
public record TimelineRevisionIdentity(String tenantId,String projectId,String revisionId) {
 public TimelineRevisionIdentity { require(tenantId);require(projectId);require(revisionId); }
 static void require(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Timeline identity required");}
 public void requireSameScope(TimelineRevisionIdentity other){java.util.Objects.requireNonNull(other);if(!tenantId.equals(other.tenantId)||!projectId.equals(other.projectId))throw new IllegalArgumentException("Timeline revision scope mismatch");}
}
