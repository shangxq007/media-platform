package com.example.platform.providerplugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts unchanged nested plugin bytes into a controlled PF4J plugin directory. */
public final class EmbeddedPluginExtractor {

    private EmbeddedPluginExtractor() {}

    public static ExtractedPlugin extractSingle(
            Path distributableJar, String entryPrefix, Path controlledPluginDirectory)
            throws IOException {
        Path outer = Objects.requireNonNull(distributableJar, "distributableJar")
                .toAbsolutePath().normalize();
        String prefix = Objects.requireNonNull(entryPrefix, "entryPrefix");
        Path directory = Objects.requireNonNull(controlledPluginDirectory, "controlledPluginDirectory")
                .toAbsolutePath().normalize();
        if (!prefix.endsWith("/") || prefix.contains("..")) {
            throw new IllegalArgumentException("entryPrefix must be a safe directory prefix");
        }
        Files.createDirectories(directory);
        try (ZipFile zip = new ZipFile(outer.toFile())) {
            List<? extends ZipEntry> candidates = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(prefix))
                    .filter(entry -> entry.getName().endsWith(".jar"))
                    .toList();
            if (candidates.size() != 1) {
                throw new ProviderPluginLoadException(
                        "EMBEDDED_PLUGIN_CARDINALITY", "expected exactly one embedded plugin JAR");
            }
            ZipEntry entry = candidates.getFirst();
            String fileName = Path.of(entry.getName()).getFileName().toString();
            Path target = directory.resolve(fileName).normalize();
            if (!target.getParent().equals(directory)) {
                throw new ProviderPluginLoadException(
                        "EMBEDDED_PLUGIN_PATH_ESCAPE", "embedded plugin target escapes controlled directory");
            }
            byte[] nestedBytes;
            try (InputStream input = zip.getInputStream(entry)) {
                nestedBytes = input.readAllBytes();
            }
            String nestedDigest = sha256(nestedBytes);
            Path temporary = Files.createTempFile(directory, fileName + ".", ".part");
            try {
                Files.write(temporary, nestedBytes);
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            String extractedDigest = sha256(Files.readAllBytes(target));
            if (!nestedDigest.equals(extractedDigest)) {
                Files.deleteIfExists(target);
                throw new ProviderPluginLoadException(
                        "EMBEDDED_PLUGIN_DIGEST_MISMATCH", "extracted plugin bytes differ");
            }
            return new ExtractedPlugin(target, nestedDigest, entry.getName());
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record ExtractedPlugin(Path pluginJar, String sha256, String sourceEntry) {}
}
