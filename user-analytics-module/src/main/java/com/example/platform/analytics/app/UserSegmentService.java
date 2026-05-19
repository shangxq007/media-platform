package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.UserBehaviorEventRepository;
import com.example.platform.analytics.infrastructure.UserProfileRepository;
import com.example.platform.analytics.infrastructure.UserSegmentRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserSegmentService {

    private static final Logger log = LoggerFactory.getLogger(UserSegmentService.class);

    private final UserSegmentRepository segmentRepository;
    private final UserProfileRepository profileRepository;
    private final UserBehaviorEventRepository eventRepository;

    public UserSegmentService(UserSegmentRepository segmentRepository,
                              UserProfileRepository profileRepository,
                              UserBehaviorEventRepository eventRepository) {
        this.segmentRepository = segmentRepository;
        this.profileRepository = profileRepository;
        this.eventRepository = eventRepository;
    }

    public UserSegment computeSegment(String tenantId, String name, String description,
                                       Map<String, String> criteria) {
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> matchedUserIds = profiles.stream()
                .filter(p -> matchesCriteria(p, criteria))
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"),
                tenantId,
                name,
                description,
                criteria,
                matchedUserIds,
                matchedUserIds.size(),
                Instant.now()
        );

        segmentRepository.save(segment);
        log.debug("Computed segment '{}' for tenant {} with {} users", name, tenantId, matchedUserIds.size());
        return segment;
    }

    public UserSegment computeActiveUsersSegment(String tenantId, int activeWithinDays) {
        Instant cutoff = Instant.now().minus(activeWithinDays, ChronoUnit.DAYS);
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> activeUserIds = profiles.stream()
                .filter(p -> p.lastActiveAt() != null && p.lastActiveAt().isAfter(cutoff))
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"),
                tenantId,
                "active_last_" + activeWithinDays + "d",
                "Users active in the last " + activeWithinDays + " days",
                Map.of("activeWithinDays", String.valueOf(activeWithinDays)),
                activeUserIds,
                activeUserIds.size(),
                Instant.now()
        );

        segmentRepository.save(segment);
        return segment;
    }

    public UserSegment computePowerUsersSegment(String tenantId, int minActions) {
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> powerUserIds = profiles.stream()
                .filter(p -> p.totalActions() >= minActions)
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"),
                tenantId,
                "power_users",
                "Users with at least " + minActions + " actions",
                Map.of("minActions", String.valueOf(minActions)),
                powerUserIds,
                powerUserIds.size(),
                Instant.now()
        );

        segmentRepository.save(segment);
        return segment;
    }

    public UserSegment computeNewUsersSegment(String tenantId, int withinDays) {
        Instant cutoff = Instant.now().minus(withinDays, ChronoUnit.DAYS);
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> newUserIds = profiles.stream()
                .filter(p -> p.firstSeenAt() != null && p.firstSeenAt().isAfter(cutoff))
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"), tenantId,
                "new_users", "Users who signed up in the last " + withinDays + " days",
                Map.of("withinDays", String.valueOf(withinDays)),
                newUserIds, newUserIds.size(), Instant.now());
        segmentRepository.save(segment);
        return segment;
    }

    public UserSegment computeAtRiskUsersSegment(String tenantId, int inactiveDays) {
        Instant cutoff = Instant.now().minus(inactiveDays, ChronoUnit.DAYS);
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> atRiskUserIds = profiles.stream()
                .filter(p -> p.lastActiveAt() != null
                        && p.lastActiveAt().isBefore(cutoff)
                        && p.totalActions() > 10)
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"), tenantId,
                "at_risk_users", "Previously active users inactive for " + inactiveDays + "+ days",
                Map.of("inactiveDays", String.valueOf(inactiveDays)),
                atRiskUserIds, atRiskUserIds.size(), Instant.now());
        segmentRepository.save(segment);
        return segment;
    }

    public UserSegment computeDormantUsersSegment(String tenantId, int dormantDays) {
        Instant cutoff = Instant.now().minus(dormantDays, ChronoUnit.DAYS);
        List<UserProfile> profiles = profileRepository.findByTenantId(tenantId, 10000);

        List<String> dormantUserIds = profiles.stream()
                .filter(p -> p.lastActiveAt() == null || p.lastActiveAt().isBefore(cutoff))
                .map(UserProfile::userId)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"), tenantId,
                "dormant_users", "Users with no activity in " + dormantDays + "+ days",
                Map.of("dormantDays", String.valueOf(dormantDays)),
                dormantUserIds, dormantUserIds.size(), Instant.now());
        segmentRepository.save(segment);
        return segment;
    }

    public UserSegment computeFailedRenderUsersSegment(String tenantId, int minFailures) {
        List<UserBehaviorEvent> failedEvents = eventRepository.findByTenantIdAndEventType(
                tenantId, "render_job", 10000);

        Map<String, Long> failureCounts = failedEvents.stream()
                .filter(e -> "failed".equals(e.action()) || "error".equals(e.action()))
                .collect(Collectors.groupingBy(UserBehaviorEvent::userId, Collectors.counting()));

        List<String> failedUserIds = failureCounts.entrySet().stream()
                .filter(e -> e.getValue() >= minFailures)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        UserSegment segment = new UserSegment(
                Ids.newId("seg"), tenantId,
                "failed_render_users", "Users with " + minFailures + "+ failed render jobs",
                Map.of("minFailures", String.valueOf(minFailures)),
                failedUserIds, failedUserIds.size(), Instant.now());
        segmentRepository.save(segment);
        return segment;
    }

    public Optional<UserSegment> findSegment(String tenantId, String segmentId) {
        return segmentRepository.findByTenantIdAndSegmentId(tenantId, segmentId);
    }

    public List<UserSegment> listSegmentsByTenant(String tenantId) {
        return segmentRepository.findByTenantId(tenantId);
    }

    private boolean matchesCriteria(UserProfile profile, Map<String, String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, String> entry : criteria.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            switch (key) {
                case "minActions":
                    if (profile.totalActions() < Integer.parseInt(value)) return false;
                    break;
                case "minSessions":
                    if (profile.totalSessions() < Integer.parseInt(value)) return false;
                    break;
                case "activeWithinDays":
                    Instant cutoff = Instant.now().minus(Integer.parseInt(value), ChronoUnit.DAYS);
                    if (profile.lastActiveAt() == null || profile.lastActiveAt().isBefore(cutoff)) return false;
                    break;
                case "usesFeature":
                    if (!profile.featureUsageCounts().containsKey(value)) return false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }
}
