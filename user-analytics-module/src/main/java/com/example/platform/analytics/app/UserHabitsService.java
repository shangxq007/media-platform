package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.UserBehaviorEventRepository;
import com.example.platform.analytics.infrastructure.UserHabitsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserHabitsService {

    private static final Logger log = LoggerFactory.getLogger(UserHabitsService.class);

    private final UserHabitsRepository habitsRepository;
    private final UserBehaviorEventRepository eventRepository;

    public UserHabitsService(UserHabitsRepository habitsRepository,
                             UserBehaviorEventRepository eventRepository) {
        this.habitsRepository = habitsRepository;
        this.eventRepository = eventRepository;
    }

    public UserHabits computeHabits(String tenantId, String userId) {
        List<UserBehaviorEvent> events = eventRepository.findByTenantIdAndUserId(tenantId, userId, 5000);

        if (events.isEmpty()) {
            return new UserHabits(tenantId, userId, Map.of(), Map.of(), List.of(), List.of(),
                    0.0, "unknown", "unknown", 0, Instant.now());
        }

        Map<String, Integer> dailyBuckets = new HashMap<>();
        Map<String, Integer> weeklyPattern = new HashMap<>();
        Map<String, Integer> featureCounts = new HashMap<>();
        Map<String, Integer> actionCounts = new HashMap<>();
        Set<LocalDate> activeDays = new HashSet<>();

        for (UserBehaviorEvent event : events) {
            Instant instant = event.occurredAt();
            ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);

            String hourKey = String.format("h%02d", zdt.getHour());
            dailyBuckets.merge(hourKey, 1, Integer::sum);

            String dayKey = zdt.getDayOfWeek().name();
            weeklyPattern.merge(dayKey, 1, Integer::sum);

            if (event.resourceType() != null) {
                featureCounts.merge(event.resourceType(), 1, Integer::sum);
            }
            if (event.action() != null) {
                actionCounts.merge(event.action(), 1, Integer::sum);
            }

            activeDays.add(zdt.toLocalDate());
        }

        String peakHour = dailyBuckets.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

        String peakDay = weeklyPattern.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

        List<String> topFeatures = featureCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> topActions = actionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        int retentionDays = computeRetentionDays(activeDays);

        UserHabits habits = new UserHabits(
                tenantId,
                userId,
                dailyBuckets,
                weeklyPattern,
                topFeatures,
                topActions,
                events.isEmpty() ? 0.0 : (double) events.size() / Math.max(activeDays.size(), 1),
                peakHour,
                peakDay,
                retentionDays,
                Instant.now()
        );

        habitsRepository.save(habits);
        log.debug("Computed habits for tenant {} user {} with {} events over {} days",
                tenantId, userId, events.size(), activeDays.size());
        return habits;
    }

    public Optional<UserHabits> findHabits(String tenantId, String userId) {
        return habitsRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    public List<UserHabits> listHabitsByTenant(String tenantId, int limit) {
        return habitsRepository.findByTenantId(tenantId, limit);
    }

    private int computeRetentionDays(Set<LocalDate> activeDays) {
        if (activeDays.isEmpty()) return 0;
        LocalDate earliest = activeDays.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate latest = activeDays.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        return (int) java.time.temporal.ChronoUnit.DAYS.between(earliest, latest) + 1;
    }
}
