package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * CFRH-I2 explicit privileged system-maintenance read authority.
 *
 * <p>System-wide / cross-project timeline reads (global project enumeration,
 * integrity sweeps, GC sweeps, storage-URI index rebuilds) MUST go through this
 * explicit port — never through ambient global lookup on a normal application
 * service. This is the single allowlisted home of
 * {@link TimelineSnapshotService#listDistinctProjectIds()} for production.
 *
 * <p>Contract: SYSTEM_CANONICAL_READ_REQUIRES_EXPLICIT_PRIVILEGED_PORT_V1.
 * The structural guard
 * ({@code Cfrhi2SystemAuthorityGuardTest}) fails closed if any other
 * production class calls the global enumeration directly.
 */
@Service
public class SystemMaintenanceReader {

    private final TimelineSnapshotService timelineSnapshotService;

    public SystemMaintenanceReader(TimelineSnapshotService timelineSnapshotService) {
        this.timelineSnapshotService = timelineSnapshotService;
    }

    /** Global enumeration of projects that have timeline snapshots (system maintenance only). */
    public List<String> listProjectIdsWithSnapshots() {
        return timelineSnapshotService.listDistinctProjectIds();
    }

    /**
     * Privileged latest-snapshot read for a project (system maintenance only).
     * System sweeps (GC, integrity, index rebuilds) have no tenant request
     * context; this port is the explicit allowlisted exception to the
     * tenant-aware ownership-scoped read rule. Normal application reads must
     * use {@code findLatestOwnedByProject(projectId, tenantId)}.
     */
    public Optional<TimelineSnapshotService.SnapshotInfo> findLatestSnapshot(String projectId) {
        return timelineSnapshotService.findLatestByProject(projectId);
    }
}
