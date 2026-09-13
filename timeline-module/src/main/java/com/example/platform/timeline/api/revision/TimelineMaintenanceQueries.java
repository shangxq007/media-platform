package com.example.platform.timeline.api.revision;
import com.example.platform.timeline.api.revision.TimelineSnapshotView;
import java.util.List;
import java.util.Optional;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineMaintenanceQueries {
List<String> listProjectIdsWithSnapshots();

Optional<TimelineSnapshotView> findLatestSnapshot(String projectId);
}
