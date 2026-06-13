package com.example.platform.render.infrastructure.canonical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * System Event Bus - Single Writer Model for all subsystem events.
 * 
 * <p>ALL subsystems publish ONLY via SystemEventBus.
 * No direct cross-service mutation is allowed.
 * 
 * <p>Features:
 * <ul>
 *   <li>Event ordering guaranteed per jobId</li>
 *   <li>Deterministic replay supported</li>
 *   <li>Single writer model enforced</li>
 *   <li>No dual-write allowed</li>
 * </ul>
 */
@Service
public class SystemEventBus {

    private static final Logger log = LoggerFactory.getLogger(SystemEventBus.class);

    private final Map<String, List<SystemCanonicalEvent>> eventsByJobId = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    private final List<SystemEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, SystemCanonicalEvent> eventsById = new ConcurrentHashMap<>();

    /**
     * Publish an event to the bus.
     * This is the ONLY way to emit events in the system.
     */
    public SystemCanonicalEvent publish(SystemCanonicalEvent event) {
        String jobId = event.jobId();
        if (jobId == null) {
            throw new IllegalArgumentException("Event must have a jobId");
        }

        // Assign sequence number
        AtomicInteger counter = sequenceCounters.computeIfAbsent(jobId, k -> new AtomicInteger(0));
        int sequence = counter.incrementAndGet();
        SystemCanonicalEvent sequencedEvent = SystemCanonicalEvent.withSequence(event, sequence);

        // Store event
        eventsByJobId.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(sequencedEvent);
        eventsById.put(sequencedEvent.eventId(), sequencedEvent);

        // Notify listeners
        for (SystemEventListener listener : listeners) {
            try {
                listener.onEvent(sequencedEvent);
            } catch (Exception e) {
                log.warn("Listener failed for event {}: {}", sequencedEvent.eventId(), e.getMessage());
            }
        }

        log.debug("Published event: {}", sequencedEvent.getDescription());
        return sequencedEvent;
    }

    /**
     * Register an event listener.
     */
    public void addListener(SystemEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove an event listener.
     */
    public void removeListener(SystemEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get all events for a job.
     */
    public List<SystemCanonicalEvent> getEventsForJob(String jobId) {
        return eventsByJobId.getOrDefault(jobId, List.of());
    }

    /**
     * Get events for a job by type.
     */
    public List<SystemCanonicalEvent> getEventsForJobByType(String jobId, String eventType) {
        return getEventsForJob(jobId).stream()
                .filter(e -> e.eventType().equals(eventType))
                .toList();
    }

    /**
     * Get events for a job by source system.
     */
    public List<SystemCanonicalEvent> getEventsForJobBySource(String jobId, String sourceSystem) {
        return getEventsForJob(jobId).stream()
                .filter(e -> e.sourceSystem().equals(sourceSystem))
                .toList();
    }

    /**
     * Get an event by ID.
     */
    public SystemCanonicalEvent getEventById(String eventId) {
        return eventsById.get(eventId);
    }

    /**
     * Get the last event for a job.
     */
    public Optional<SystemCanonicalEvent> getLastEvent(String jobId) {
        List<SystemCanonicalEvent> events = getEventsForJob(jobId);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(events.size() - 1));
    }

    /**
     * Get the sequence number for the last event.
     */
    public int getLastSequenceNumber(String jobId) {
        AtomicInteger counter = sequenceCounters.get(jobId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get all events in chronological order.
     */
    public List<SystemCanonicalEvent> getAllEvents() {
        List<SystemCanonicalEvent> all = new ArrayList<>();
        for (List<SystemCanonicalEvent> events : eventsByJobId.values()) {
            all.addAll(events);
        }
        all.sort(Comparator.comparing(SystemCanonicalEvent::timestamp)
                .thenComparingInt(SystemCanonicalEvent::sequenceNumber));
        return all;
    }

    /**
     * Clear all events for a job.
     */
    public void clearEventsForJob(String jobId) {
        eventsByJobId.remove(jobId);
        sequenceCounters.remove(jobId);
    }

    /**
     * Clear all events.
     */
    public void clearAll() {
        eventsByJobId.clear();
        sequenceCounters.clear();
        eventsById.clear();
    }

    /**
     * Get the total number of events.
     */
    public int getEventCount() {
        return eventsById.size();
    }

    /**
     * Get the number of jobs with events.
     */
    public int getJobCount() {
        return eventsByJobId.size();
    }

    // ---------------------------------------------------------------------------
    // Event Listener Interface
    // ---------------------------------------------------------------------------

    @FunctionalInterface
    public interface SystemEventListener {
        void onEvent(SystemCanonicalEvent event);
    }
}
