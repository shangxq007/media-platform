package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.UserBehaviorEventRepository;
import com.example.platform.analytics.infrastructure.UserProfileRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository profileRepository;
    private final UserBehaviorEventRepository eventRepository;

    public UserProfileService(UserProfileRepository profileRepository,
                              UserBehaviorEventRepository eventRepository) {
        this.profileRepository = profileRepository;
        this.eventRepository = eventRepository;
    }

    public UserProfile getOrCreateProfile(String tenantId, String userId) {
        return profileRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> createDefaultProfile(tenantId, userId));
    }

    public UserProfile aggregateProfile(String tenantId, String userId) {
        List<UserBehaviorEvent> events = eventRepository.findByTenantIdAndUserId(tenantId, userId, 1000);

        if (events.isEmpty()) {
            return getOrCreateProfile(tenantId, userId);
        }

        Map<String, Integer> featureCounts = new HashMap<>();
        Map<String, Integer> actionCounts = new HashMap<>();
        Set<String> languages = new HashSet<>();
        Instant firstSeen = events.get(events.size() - 1).occurredAt();
        Instant lastActive = events.get(0).occurredAt();

        for (UserBehaviorEvent event : events) {
            if (event.resourceType() != null) {
                featureCounts.merge(event.resourceType(), 1, Integer::sum);
            }
            if (event.action() != null) {
                actionCounts.merge(event.action(), 1, Integer::sum);
            }
            if (event.metadata() != null) {
                String lang = event.metadata().get("language");
                if (lang != null && !lang.isBlank()) {
                    languages.add(lang);
                }
            }
            if (event.occurredAt().isBefore(firstSeen)) {
                firstSeen = event.occurredAt();
            }
            if (event.occurredAt().isAfter(lastActive)) {
                lastActive = event.occurredAt();
            }
        }

        UserProfile profile = new UserProfile(
                Ids.newId("prof"),
                tenantId,
                userId,
                null,
                languages,
                featureCounts,
                actionCounts,
                events.size(),
                events.size(),
                firstSeen,
                lastActive,
                Instant.now()
        );

        profileRepository.save(profile);
        log.debug("Aggregated profile for tenant {} user {} with {} events", tenantId, userId, events.size());
        return profile;
    }

    public List<UserProfile> listProfilesByTenant(String tenantId, int limit) {
        return profileRepository.findByTenantId(tenantId, limit);
    }

    public Optional<UserProfile> findProfile(String tenantId, String userId) {
        return profileRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    private UserProfile createDefaultProfile(String tenantId, String userId) {
        UserProfile profile = new UserProfile(
                Ids.newId("prof"),
                tenantId,
                userId,
                null,
                Set.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        profileRepository.save(profile);
        return profile;
    }
}
