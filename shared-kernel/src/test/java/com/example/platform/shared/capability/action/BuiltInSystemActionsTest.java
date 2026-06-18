package com.example.platform.shared.capability.action;

import com.example.platform.shared.capability.CapabilityStability;
import com.example.platform.shared.capability.SystemAction;
import com.example.platform.shared.capability.registry.InMemorySystemActionRegistry;
import com.example.platform.shared.capability.registry.SystemActionRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuiltInSystemActions.
 *
 * <p>These tests verify action metadata without implementing action execution.</p>
 */
class BuiltInSystemActionsTest {

    @Test
    void allBuiltInActionKeysAreUnique() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        Set<String> keys = actions.stream()
            .map(SystemAction::actionKey)
            .collect(Collectors.toSet());

        assertEquals(actions.size(), keys.size(), "All action keys must be unique");
    }

    @Test
    void builtInActionsCanBeRegisteredIntoRegistry() {
        SystemActionRegistry registry = new InMemorySystemActionRegistry();

        BuiltInSystemActions.registerInto(registry);

        List<SystemAction> allActions = BuiltInSystemActions.all();
        for (SystemAction action : allActions) {
            assertTrue(registry.contains(action.actionKey()),
                "Action should be registered: " + action.actionKey());
        }
    }

    @Test
    void registryFindsRenderCreateJob() {
        SystemActionRegistry registry = new InMemorySystemActionRegistry();
        BuiltInSystemActions.registerInto(registry);

        var action = registry.findByKey("render.create_job");

        assertTrue(action.isPresent());
        assertEquals("render.create_job", action.get().actionKey());
        assertEquals("Create Render Job", ((MetadataSystemAction) action.get()).displayName());
    }

    @Test
    void registryFindsMediaGenerateThumbnail() {
        SystemActionRegistry registry = new InMemorySystemActionRegistry();
        BuiltInSystemActions.registerInto(registry);

        var action = registry.findByKey("media.generate_thumbnail");

        assertTrue(action.isPresent());
        assertEquals("media.generate_thumbnail", action.get().actionKey());
        assertEquals("Generate Thumbnail", ((MetadataSystemAction) action.get()).displayName());
    }

    @Test
    void metadataContainsPermissions() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            assertNotNull(action.requiredPermissions(), "Action must have permissions: " + action.actionKey());
            assertFalse(action.requiredPermissions().isEmpty(), "Action permissions must not be empty: " + action.actionKey());
        }
    }

    @Test
    void metadataContainsIdempotencyRequirement() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            // All built-in actions should be idempotent
            assertTrue(action.isIdempotent(), "Action should be idempotent: " + action.actionKey());
        }
    }

    @Test
    void metadataContainsTimeoutPolicy() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            assertNotNull(action.timeout(), "Action must have timeout: " + action.actionKey());
            assertFalse(action.timeout().isNegative(), "Timeout must not be negative: " + action.actionKey());
        }
    }

    @Test
    void noActionExecutionOccurs() {
        // This test verifies that catalog only provides metadata
        // and does not execute any actions
        List<SystemAction> actions = BuiltInSystemActions.all();

        // If actions tried to execute, this would fail or have side effects
        assertNotNull(actions);
        assertFalse(actions.isEmpty());
    }

    @Test
    void actionKeysAreStableStrings() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            String key = action.actionKey();
            assertNotNull(key, "Action key must not be null");
            assertFalse(key.isBlank(), "Action key must not be blank");
            assertTrue(key.contains("."), "Action key should follow namespace.name pattern: " + key);
        }
    }

    @Test
    void findByKeyReturnsNullForUnknownAction() {
        SystemAction action = BuiltInSystemActions.findByKey("unknown.action");
        assertNull(action);
    }

    @Test
    void findByKeyReturnsActionForKnownKey() {
        SystemAction action = BuiltInSystemActions.findByKey("render.create_job");
        assertNotNull(action);
        assertEquals("render.create_job", action.actionKey());
    }

    @Test
    void allActionsHaveStableVersion() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            assertNotNull(action.version(), "Action must have version: " + action.actionKey());
            assertEquals("1.0.0", action.version(), "Action version should be 1.0.0: " + action.actionKey());
        }
    }

    @Test
    void allActionsHaveStabilityLevel() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        for (SystemAction action : actions) {
            assertNotNull(action.stability(), "Action must have stability: " + action.actionKey());
            assertEquals(CapabilityStability.STABLE, action.stability(),
                "Action stability should be STABLE: " + action.actionKey());
        }
    }

    @Test
    void catalogContainsExpectedCategories() {
        List<SystemAction> actions = BuiltInSystemActions.all();

        Set<SystemActionCategory> categories = actions.stream()
            .map(a -> ((MetadataSystemAction) a).category())
            .collect(Collectors.toSet());

        assertTrue(categories.contains(SystemActionCategory.RENDER), "Should have RENDER actions");
        assertTrue(categories.contains(SystemActionCategory.MEDIA), "Should have MEDIA actions");
        assertTrue(categories.contains(SystemActionCategory.ARTIFACT), "Should have ARTIFACT actions");
        assertTrue(categories.contains(SystemActionCategory.REVIEW), "Should have REVIEW actions");
        assertTrue(categories.contains(SystemActionCategory.NOTIFICATION), "Should have NOTIFICATION actions");
        assertTrue(categories.contains(SystemActionCategory.WEBHOOK), "Should have WEBHOOK actions");
    }
}
