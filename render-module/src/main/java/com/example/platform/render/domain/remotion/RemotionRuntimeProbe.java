package com.example.platform.render.domain.remotion;

import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import java.util.ArrayList;
import java.util.List;

/**
 * Probes Remotion runtime availability using RenderToolCapabilityInventory.
 *
 * <p>Internal only — diagnostic, not execution permission.</p>
 *
 * <p>Safe probes only: node --version, npm --version, npx --version.
 * Does NOT run npx remotion, npm install, or any network/package commands.</p>
 */
public class RemotionRuntimeProbe {

    private final RenderToolCapabilityInventory inventory;

    public RemotionRuntimeProbe(RenderToolCapabilityInventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Probe Remotion runtime availability.
     */
    public RemotionRuntimeAvailability probe() {
        List<RemotionRuntimeToolStatus> toolStatuses = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        // Probe node
        var nodeEntry = inventory.detectTools().stream()
                .filter(e -> "node".equals(e.name()))
                .findFirst();
        boolean nodeAvailable = nodeEntry.map(RenderToolCapabilityInventory.ToolInventoryEntry::available).orElse(false);
        String nodeVersion = nodeEntry.flatMap(e -> java.util.Optional.ofNullable(e.version())).orElse(null);
        if (nodeAvailable) {
            toolStatuses.add(RemotionRuntimeToolStatus.available("node", nodeVersion));
        } else {
            toolStatuses.add(RemotionRuntimeToolStatus.missing("node"));
            issues.add("node not available");
        }

        // Probe npm
        var npmEntry = inventory.detectTools().stream()
                .filter(e -> "npm".equals(e.name()))
                .findFirst();
        boolean npmAvailable = npmEntry.map(RenderToolCapabilityInventory.ToolInventoryEntry::available).orElse(false);
        String npmVersion = npmEntry.flatMap(e -> java.util.Optional.ofNullable(e.version())).orElse(null);
        if (npmAvailable) {
            toolStatuses.add(RemotionRuntimeToolStatus.available("npm", npmVersion));
        } else {
            toolStatuses.add(RemotionRuntimeToolStatus.missing("npm"));
            issues.add("npm not available");
        }

        // Probe npx
        var npxEntry = inventory.detectTools().stream()
                .filter(e -> "npx".equals(e.name()))
                .findFirst();
        boolean npxAvailable = npxEntry.map(RenderToolCapabilityInventory.ToolInventoryEntry::available).orElse(false);
        String npxVersion = npxEntry.flatMap(e -> java.util.Optional.ofNullable(e.version())).orElse(null);
        if (npxAvailable) {
            toolStatuses.add(RemotionRuntimeToolStatus.available("npx", npxVersion));
        } else {
            toolStatuses.add(RemotionRuntimeToolStatus.missing("npx"));
            issues.add("npx not available");
        }

        // Remotion CLI: NOT probed (would require npx remotion which may download)
        toolStatuses.add(new RemotionRuntimeToolStatus(
                "remotion-cli", RemotionRuntimeAvailabilityStatus.NOT_CHECKED,
                null, "Not probed — would require npx remotion which may download packages"));

        return new RemotionRuntimeAvailability(
                nodeAvailable, nodeVersion,
                npmAvailable, npmVersion,
                npxAvailable, npxVersion,
                false, null,  // remotionCliAvailable=false, not probed
                true,         // documentGenerationReady
                false,        // executionReady — always false
                true,         // disabledByPolicy — always true
                List.copyOf(toolStatuses),
                List.copyOf(issues));
    }
}
