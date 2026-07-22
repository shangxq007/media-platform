package com.example.platform.typedschema.guard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the stable allowlist of approved untyped jOOQ DSL call sites.
 *
 * <p>The allowlist records sites that have been reviewed and approved
 * as intentional debt. New untyped calls outside the allowlist will
 * fail the guard check.</p>
 *
 * <p>Format: one entry per line, {@code stableSiteId} format is
 * {@code filePath:lineNumber}. Lines starting with {@code #} are comments.</p>
 */
public final class AllowlistRegistry {

    private static final String COMMENT_PREFIX = "#";

    private AllowlistRegistry() {
        // utility class
    }

    /**
     * Load an allowlist file, returning the set of allowed site IDs.
     *
     * @param allowlistFile path to the allowlist file
     * @return set of allowed stable site IDs; empty set if file doesn't exist
     * @throws IOException if the file cannot be read
     */
    public static Set<String> load(Path allowlistFile) throws IOException {
        Set<String> allowed = new LinkedHashSet<>();
        if (!Files.exists(allowlistFile)) {
            return allowed;
        }
        for (String line : Files.readAllLines(allowlistFile)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith(COMMENT_PREFIX)) {
                // Support both plain site ID and pipe-delimited format
                String siteId = trimmed.contains("|")
                    ? trimmed.split("\\|")[0].trim()
                    : trimmed;
                allowed.add(siteId);
            }
        }
        return allowed;
    }

    /**
     * Save an allowlist to a file.
     *
     * @param allowlistFile path to the allowlist file
     * @param siteIds       set of site IDs to persist
     * @param header        comment header to prepend
     * @throws IOException if the file cannot be written
     */
    public static void save(Path allowlistFile, Set<String> siteIds, String header) throws IOException {
        List<String> lines = new ArrayList<>();
        if (header != null && !header.isEmpty()) {
            for (String h : header.split("\n")) {
                lines.add("# " + h);
            }
        }
        for (String siteId : siteIds) {
            lines.add(siteId);
        }
        Files.write(allowlistFile, lines);
    }

    /**
     * Filter violations against the allowlist, returning only non-allowed violations.
     *
     * @param violations  all detected violations
     * @param allowedSiteIds set of approved site IDs
     * @return violations that are NOT in the allowlist
     */
    public static List<JooqUntypedCallGuard.UntypedCallViolation> filterViolations(
            List<JooqUntypedCallGuard.UntypedCallViolation> violations,
            Set<String> allowedSiteIds) {
        return violations.stream()
            .filter(v -> !allowedSiteIds.contains(v.stableSiteId()))
            .toList();
    }

    /**
     * Check whether an allowlist file has no duplicate entries.
     *
     * @param allowlistFile path to the allowlist file
     * @return true if no duplicates found (or file doesn't exist)
     * @throws IOException if the file cannot be read
     */
    public static boolean checkNoDuplicates(Path allowlistFile) throws IOException {
        if (!Files.exists(allowlistFile)) {
            return true;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String line : Files.readAllLines(allowlistFile)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith(COMMENT_PREFIX)) {
                String siteId = trimmed.contains("|")
                    ? trimmed.split("\\|")[0].trim()
                    : trimmed;
                if (!seen.add(siteId)) {
                    return false; // duplicate found
                }
            }
        }
        return true;
    }
}
