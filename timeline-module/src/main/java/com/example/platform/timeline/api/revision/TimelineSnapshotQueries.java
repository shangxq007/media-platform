package com.example.platform.timeline.api.revision;
import java.util.Optional;
/** Scoped snapshot projection. No write or global maintenance surface. */
public interface TimelineSnapshotQueries {
 Optional<TimelineSnapshotView> findOwnedById(String projectId,String tenantId,String snapshotId);
 Optional<TimelineSnapshotView> findLatestOwnedByProject(String projectId,String tenantId);
}
