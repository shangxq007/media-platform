package com.example.platform.extension.domain;

import java.util.Set;

/**
 * Permission descriptor (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 vocabulary (A042) — the minimum for FFmpeg/render self-description:
 * {@code ffmpeg.execute}, {@code temporary-file.write}, {@code asset.read},
 * {@code storage.read}, {@code cpu.use}, {@code memory.use},
 * {@code font.read}. All P1 permissions are DECLARATION-ONLY: validated for
 * recognition, not actively enforced at invocation (P1 introduces no governed
 * invocation path). No speculative permissions are added.</p>
 *
 * <p>Unknown permission IDs fail descriptor validation (PERM-UNKNOWN /
 * PLG-010). Raw credentials are never part of any descriptor.</p>
 *
 * @param permissionId stable permission ID from the frozen vocabulary
 */
public record PermissionDescriptor(String permissionId) {

    /** Frozen P1 permission vocabulary (minimum for FFmpeg/render). */
    public static final Set<String> KNOWN_PERMISSION_IDS = Set.of(
            "ffmpeg.execute",
            "temporary-file.write",
            "asset.read",
            "storage.read",
            "cpu.use",
            "memory.use",
            "font.read");

    public PermissionDescriptor {
        if (permissionId == null) {
            throw new NullPointerException("permissionId must not be null");
        }
        permissionId = permissionId.trim();
    }
}
