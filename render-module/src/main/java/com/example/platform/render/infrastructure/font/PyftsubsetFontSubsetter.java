package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Feature-flagged Pyftsubset font subsetter.
 *
 * Enabled via: render.font.subset.enabled=true
 *
 * Requires fontTools with pyftsubset:
 *   pip install fonttools
 *
 * Usage:
 *   pyftsubset font.ttf --text-file=chars.txt --output-file=subset.woff2 \
 *     --flavor=woff2 --layout-features='kern,liga,calt'
 *
 * Cache key = SHA256(fontHash + charsHash + subsetOptionsHash)
 */
public class PyftsubsetFontSubsetter implements FontSubsetter {
    private static final Logger log = LoggerFactory.getLogger(PyftsubsetFontSubsetter.class);

    private boolean enabled = false;
    private Path cacheDir = Path.of(System.getProperty("java.io.tmpdir"), "font-subsets");

    public PyftsubsetFontSubsetter enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public PyftsubsetFontSubsetter cacheDir(Path dir) {
        this.cacheDir = dir;
        return this;
    }

    @Override
    public String subsetterName() {
        return "PyftsubsetFontSubsetter";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public FontSubsetResult subset(Path fontFile, Set<Integer> codePoints, SubsetOptions options) {
        if (!enabled) {
            log.warn("PyftsubsetFontSubsetter is disabled. Enable via render.font.subset.enabled=true");
            return new FontSubsetResult("pyftsubset", false, null, null, "ttf", 0, 0, 0,
                    List.of(), Map.of());
        }

        String fontHash = computeFileHash(fontFile);
        String charsHash = computeCharsHash(codePoints);
        String optionsHash = computeOptionsHash(options);
        String cacheKey = computeCacheKey(fontHash, charsHash, optionsHash);

        if (Files.exists(cacheDir.resolve(cacheKey + ".woff2"))) {
            log.info("Subset cache hit: {}", cacheKey);
            return new FontSubsetResult("pyftsubset", true, cacheKey,
                    cacheDir.resolve(cacheKey + ".woff2").toString(),
                    "woff2", 0, codePoints.size(), codePoints.size(),
                    List.of(), Map.of());
        }

        try {
            Path charsFile = Files.createTempFile("font-chars-", ".txt");
            Path outputFile = cacheDir.resolve(cacheKey + ".woff2");

            try {
                StringBuilder sb = new StringBuilder();
                codePoints.stream().sorted().forEach(cp -> sb.appendCodePoint(cp));
                Files.writeString(charsFile, sb.toString());

                List<String> cmd = new ArrayList<>();
                cmd.add("pyftsubset");
                cmd.add(fontFile.toString());
                cmd.add("--text-file=" + charsFile);
                cmd.add("--output-file=" + outputFile);
                cmd.add("--flavor=" + options.format());
                if (!options.layoutFeatures().isEmpty()) {
                    cmd.add("--layout-features=" + String.join(",", options.layoutFeatures()));
                }
                if (options.desubroutinize()) cmd.add("--desubroutinize");
                if (options.notdefOutline()) cmd.add("--notdef-outline");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                String errors = new String(process.getErrorStream().readAllBytes());
                boolean finished = process.waitFor(60, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    log.error("pyftsubset timed out for: {}", fontFile);
                    return new FontSubsetResult("pyftsubset", false, cacheKey, null, "ttf", 0, 0, 0,
                            List.of(), Map.of());
                }

                if (process.exitValue() != 0) {
                    log.error("pyftsubset failed: {}", errors);
                    return new FontSubsetResult("pyftsubset", false, cacheKey, null, "ttf", 0, 0, 0,
                            List.of(), Map.of());
                }

                long size = Files.exists(outputFile) ? Files.size(outputFile) : 0;
                return new FontSubsetResult("pyftsubset", true, cacheKey,
                        outputFile.toString(), options.format(),
                        size, 0, codePoints.size(),
                        List.of(), Map.of());
            } finally {
                Files.deleteIfExists(charsFile);
            }
        } catch (Exception e) {
            log.error("pyftsubset error for {}: {}", fontFile, e.getMessage());
            return new FontSubsetResult("pyftsubset", false, cacheKey, null, "ttf", 0, 0, 0,
                    List.of(), Map.of());
        }
    }

    public String computeCacheKey(String fontHash, String charsHash, String optionsHash) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String combined = fontHash + ":" + charsHash + ":" + optionsHash;
            byte[] hash = md.digest(combined.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String computeFileHash(Path file) {
        try {
            byte[] content = Files.readAllBytes(file);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String computeCharsHash(Set<Integer> codePoints) {
        try {
            List<Integer> sorted = codePoints.stream().sorted().toList();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (int cp : sorted) {
                md.update((byte) (cp >> 24));
                md.update((byte) (cp >> 16));
                md.update((byte) (cp >> 8));
                md.update((byte) cp);
            }
            byte[] hash = md.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String computeOptionsHash(SubsetOptions options) {
        try {
            String combined = options.format() + options.hinting() + options.kerning()
                    + options.ligatures() + options.layoutFeatures() + options.desubroutinize();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
