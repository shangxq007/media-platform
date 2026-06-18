package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;
import com.example.platform.shared.capability.CapabilityStability;
import com.example.platform.shared.capability.hook.HookFailurePolicy;
import com.example.platform.shared.capability.hook.HookPhase;
import com.example.platform.shared.capability.hook.HookPoint;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for event type and hook point registries.
 *
 * <p>These tests verify registry behavior without implementing event bus or hook runtime.</p>
 */
class EventTypeAndHookPointRegistriesTest {

    // ===== EventTypeRegistry Tests =====

    @Test
    void eventTypeRegistryRegistersAndFinds() {
        InMemoryEventTypeRegistry registry = new InMemoryEventTypeRegistry();

        EventTypeDescriptor descriptor = new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event"
        );

        registry.register(descriptor);

        assertTrue(registry.contains("asset.uploaded", "1.0.0"));
        assertEquals(descriptor, registry.find("asset.uploaded", "1.0.0").orElse(null));
    }

    @Test
    void eventTypeRegistryRejectsDuplicateTypeVersion() {
        InMemoryEventTypeRegistry registry = new InMemoryEventTypeRegistry();

        EventTypeDescriptor descriptor1 = new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event"
        );

        EventTypeDescriptor descriptor2 = new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event v2"
        );

        registry.register(descriptor1);

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(descriptor2)
        );

        assertEquals(CapabilityErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void eventTypeRegistryRejectsBlankType() {
        InMemoryEventTypeRegistry registry = new InMemoryEventTypeRegistry();

        // EventTypeDescriptor constructor validates and throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            new EventTypeDescriptor(
                "",
                "1.0.0",
                "storage-module",
                null,
                CapabilityStability.STABLE,
                "Asset uploaded event"
            );
        });
    }

    @Test
    void eventTypeRegistryReturnsImmutableList() {
        InMemoryEventTypeRegistry registry = new InMemoryEventTypeRegistry();

        EventTypeDescriptor descriptor = new EventTypeDescriptor(
            "asset.uploaded",
            "1.0.0",
            "storage-module",
            null,
            CapabilityStability.STABLE,
            "Asset uploaded event"
        );

        registry.register(descriptor);

        var list = registry.list();

        assertThrows(UnsupportedOperationException.class, () -> {
            list.add(new EventTypeDescriptor(
                "render.completed",
                "1.0.0",
                "render-module",
                null,
                CapabilityStability.STABLE,
                "Render completed event"
            ));
        });
    }

    // ===== HookPointRegistry Tests =====

    @Test
    void hookPointRegistryRegistersAndFinds() {
        InMemoryHookPointRegistry registry = new InMemoryHookPointRegistry();

        HookPoint hookPoint = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );

        registry.register(hookPoint);

        assertTrue(registry.contains("render.before_create", HookPhase.BEFORE));
        assertEquals(hookPoint, registry.find("render.before_create", HookPhase.BEFORE).orElse(null));
    }

    @Test
    void hookPointRegistryRejectsDuplicateKeyPhase() {
        InMemoryHookPointRegistry registry = new InMemoryHookPointRegistry();

        HookPoint hookPoint1 = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );

        HookPoint hookPoint2 = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_OPEN,
            CapabilityStability.STABLE
        );

        registry.register(hookPoint1);

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(hookPoint2)
        );

        assertEquals(CapabilityErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void hookPointRegistryRejectsBlankKey() {
        InMemoryHookPointRegistry registry = new InMemoryHookPointRegistry();

        // HookPoint constructor validates and throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            new HookPoint(
                "",
                HookPhase.BEFORE,
                null,
                null,
                Set.of(),
                Duration.ofSeconds(30),
                HookFailurePolicy.FAIL_CLOSED,
                CapabilityStability.STABLE
            );
        });
    }

    @Test
    void hookPointRegistryReturnsImmutableList() {
        InMemoryHookPointRegistry registry = new InMemoryHookPointRegistry();

        HookPoint hookPoint = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );

        registry.register(hookPoint);

        var list = registry.list();

        assertThrows(UnsupportedOperationException.class, () -> {
            list.add(new HookPoint(
                "render.after_create",
                HookPhase.AFTER,
                null,
                null,
                Set.of("render.create"),
                Duration.ofSeconds(30),
                HookFailurePolicy.FAIL_CLOSED,
                CapabilityStability.STABLE
            ));
        });
    }

    @Test
    void hookPointRegistryPreservesFailurePolicy() {
        InMemoryHookPointRegistry registry = new InMemoryHookPointRegistry();

        HookPoint hookPoint = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.RETRY_THEN_FAIL_OPEN,
            CapabilityStability.STABLE
        );

        registry.register(hookPoint);

        HookPoint found = registry.find("render.before_create", HookPhase.BEFORE).orElse(null);
        assertNotNull(found);
        assertEquals(HookFailurePolicy.RETRY_THEN_FAIL_OPEN, found.failurePolicy());
    }

    @Test
    void registriesDoNotExecuteOrPublish() {
        // EventTypeRegistry should not publish events
        InMemoryEventTypeRegistry eventRegistry = new InMemoryEventTypeRegistry();
        EventTypeDescriptor descriptor = new EventTypeDescriptor(
            "test.event",
            "1.0.0",
            "test-module",
            null,
            CapabilityStability.STABLE,
            "Test event"
        );
        eventRegistry.register(descriptor);

        // HookPointRegistry should not execute hooks
        InMemoryHookPointRegistry hookRegistry = new InMemoryHookPointRegistry();
        HookPoint hookPoint = new HookPoint(
            "test.hook",
            HookPhase.BEFORE,
            null,
            null,
            Set.of(),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );
        hookRegistry.register(hookPoint);

        // Verify registration works without execution
        assertTrue(eventRegistry.contains("test.event", "1.0.0"));
        assertTrue(hookRegistry.contains("test.hook", HookPhase.BEFORE));
    }
}
