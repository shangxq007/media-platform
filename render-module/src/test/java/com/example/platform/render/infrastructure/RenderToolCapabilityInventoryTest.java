package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderToolCapabilityInventory}.
 *
 * <p>Verifies tool detection behavior without requiring specific tools to be installed.
 * Missing tools are reported as unavailable, not failure.</p>
 */
class RenderToolCapabilityInventoryTest {

    private RenderToolCapabilityInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new RenderToolCapabilityInventory();
    }

    @Test
    void detectToolsReturnsNonNullList() {
        List<RenderToolCapabilityInventory.ToolInventoryEntry> entries = inventory.detectTools();
        assertNotNull(entries, "Tool list must not be null");
        assertFalse(entries.isEmpty(), "Tool list must not be empty");
    }

    @Test
    void allEntriesHaveName() {
        for (RenderToolCapabilityInventory.ToolInventoryEntry entry : inventory.detectTools()) {
            assertNotNull(entry.name(), "Tool name must not be null");
            assertFalse(entry.name().isBlank(), "Tool name must not be blank");
        }
    }

    @Test
    void allEntriesHaveBinary() {
        for (RenderToolCapabilityInventory.ToolInventoryEntry entry : inventory.detectTools()) {
            assertNotNull(entry.binary(), "Binary name must not be null");
            assertFalse(entry.binary().isBlank(), "Binary name must not be blank");
        }
    }

    @Test
    void availableToolsHaveVersion() {
        for (RenderToolCapabilityInventory.ToolInventoryEntry entry : inventory.detectTools()) {
            if (entry.available()) {
                assertNotNull(entry.version(), "Available tool must have version");
                assertFalse(entry.version().isBlank(), "Version must not be blank");
            }
        }
    }

    @Test
    void unavailableToolsHaveNullVersion() {
        for (RenderToolCapabilityInventory.ToolInventoryEntry entry : inventory.detectTools()) {
            if (!entry.available()) {
                assertNull(entry.version(), "Unavailable tool must have null version");
            }
        }
    }

    @Test
    void getAvailabilitySummaryReturnsMap() {
        Map<String, Boolean> summary = inventory.getAvailabilitySummary();
        assertNotNull(summary, "Summary must not be null");
        assertFalse(summary.isEmpty(), "Summary must not be empty");
    }

    @Test
    void summaryMatchesDetectTools() {
        Map<String, Boolean> summary = inventory.getAvailabilitySummary();
        List<RenderToolCapabilityInventory.ToolInventoryEntry> entries = inventory.detectTools();

        assertEquals(entries.size(), summary.size(), "Summary size must match entries size");
        for (RenderToolCapabilityInventory.ToolInventoryEntry entry : entries) {
            assertTrue(summary.containsKey(entry.name()), "Summary must contain " + entry.name());
            assertEquals(entry.available(), summary.get(entry.name()),
                    "Availability must match for " + entry.name());
        }
    }

    @Test
    void isToolAvailableReturnsFalseForUnknownTool() {
        assertFalse(inventory.isToolAvailable("nonexistent-tool-xyz"),
                "Unknown tool must return false");
    }

    @Test
    void nonProviderToolsRemainInventoryedWithoutConcreteProviderExecutionTools() {
        String[] expectedTools = {"melt", "blender", "node", "npm"};
        List<String> toolNames = inventory.detectTools().stream()
                .map(RenderToolCapabilityInventory.ToolInventoryEntry::name)
                .toList();

        for (String expected : expectedTools) {
            assertTrue(toolNames.contains(expected),
                    "Inventory must contain tool: " + expected);
        }
        assertFalse(toolNames.contains("provider-a"),
                "Render inventory must not retain concrete provider execution authority");
        assertFalse(toolNames.contains("media probe"),
                "Render inventory must not retain render-owned media probe authority");
    }

    @Test
    void toolDetectionDoesNotThrow() {
        // Verify that detection is safe and doesn't throw for any tool
        assertDoesNotThrow(() -> {
            List<RenderToolCapabilityInventory.ToolInventoryEntry> entries = inventory.detectTools();
            for (RenderToolCapabilityInventory.ToolInventoryEntry entry : entries) {
                assertNotNull(entry.name());
                assertNotNull(entry.binary());
            }
        }, "Tool detection must not throw");
    }

    @Test
    void toolDetectionIsIdempotent() {
        // Verify that repeated calls return consistent results
        List<RenderToolCapabilityInventory.ToolInventoryEntry> first = inventory.detectTools();
        List<RenderToolCapabilityInventory.ToolInventoryEntry> second = inventory.detectTools();

        assertEquals(first.size(), second.size(), "Inventory size must be consistent");
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).name(), second.get(i).name(), "Tool names must be consistent");
            assertEquals(first.get(i).available(), second.get(i).available(),
                    "Tool availability must be consistent for " + first.get(i).name());
        }
    }
}
