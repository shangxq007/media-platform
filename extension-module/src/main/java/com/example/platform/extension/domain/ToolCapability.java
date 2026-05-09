package com.example.platform.extension.domain;

/**
 * Describes a capability that a media tool supports.
 *
 * <p>Capabilities enable fine-grained routing beyond simple profile matching.
 * Examples: {@code "h264"}, {@code "h265"}, {@code "4k"}, {@code "watermark"},
 * {@code "subtitle-burn"}, {@code "dash-packaging"}.</p>
 *
 * @param name        the capability identifier (e.g., "h264")
 * @param description human-readable description of the capability
 */
public record ToolCapability(String name, String description) {
}
