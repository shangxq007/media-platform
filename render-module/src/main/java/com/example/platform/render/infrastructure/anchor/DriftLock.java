package com.example.platform.render.infrastructure.anchor;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drift Lock Mechanism - freezes SEL mutations when drift exceeds threshold.
 * 
 * <p>If drift exceeds threshold:
 * <ul>
 *   <li>Freeze SEL mutations</li>
 *   <li>Require rollback or human approval</li>
 *   <li>Log drift event in SystemCanonicalGraph</li>
 * </ul>
 */
@Service
public class DriftLock {

    private static final Logger log = LoggerFactory.getLogger(DriftLock.class);

    // Lock configuration
    private static final double DRIFT_THRESHOLD = 0.5;
    private static final long LOCK_DURATION_MINUTES = 60;
    private static final int MAX_CONSECUTIVE_DRIFTS = 3;

    private final SystemEventBus eventBus;
    private final Map<String, LockState> lockStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveDrifts = new ConcurrentHashMap<>();

    public DriftLock(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Check if a tenant is locked.
     */
    public boolean isLocked(String tenantId) {
        LockState state = lockStates.get(tenantId);
        if (state == null) return false;

        // Check if lock has expired
        if (state.lockedUntil().isBefore(Instant.now())) {
            lockStates.remove(tenantId);
            log.info("Drift lock expired for tenant {}", tenantId);
            return false;
        }

        return state.locked();
    }

    /**
     * Activate drift lock for a tenant.
     */
    public void activate(String tenantId, String reason) {
        int drifts = consecutiveDrifts.getOrDefault(tenantId, 0) + 1;
        consecutiveDrifts.put(tenantId, drifts);

        // Extend lock duration for consecutive drifts
        long lockMinutes = LOCK_DURATION_MINUTES * drifts;
        Instant lockedUntil = Instant.now().plus(lockMinutes, ChronoUnit.MINUTES);

        LockState state = new LockState(
                tenantId,
                true,
                reason,
                Instant.now(),
                lockedUntil,
                drifts
        );
        lockStates.put(tenantId, state);

        // Emit drift lock event
        emitDriftLockEvent(tenantId, state);

        log.warn("Drift lock activated for tenant {} until {} (reason: {}, consecutive: {})",
                tenantId, lockedUntil, reason, drifts);
    }

    /**
     * Deactivate drift lock for a tenant.
     */
    public void deactivate(String tenantId) {
        lockStates.remove(tenantId);
        consecutiveDrifts.remove(tenantId);
        log.info("Drift lock deactivated for tenant {}", tenantId);
    }

    /**
     * Get lock state for a tenant.
     */
    public LockState getLockState(String tenantId) {
        return lockStates.get(tenantId);
    }

    /**
     * Get consecutive drift count.
     */
    public int getConsecutiveDrifts(String tenantId) {
        return consecutiveDrifts.getOrDefault(tenantId, 0);
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private void emitDriftLockEvent(String tenantId, LockState state) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "DRIFT_LOCK_ACTIVATED",
                tenantId, null, null,
                "DriftLock",
                Map.of(
                        "reason", state.reason(),
                        "lockedUntil", state.lockedUntil().toString(),
                        "consecutiveDrifts", state.consecutiveDrifts()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record LockState(
            String tenantId,
            boolean locked,
            String reason,
            Instant lockedAt,
            Instant lockedUntil,
            int consecutiveDrifts
    ) {
        public long remainingMinutes() {
            return ChronoUnit.MINUTES.between(Instant.now(), lockedUntil);
        }
    }
}
