package com.example.platform.bmf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** Checks the exact prepared BMF distribution before the Worker advertises runtime evidence. */
public final class BmfRuntimeBundleVerifier {
    private BmfRuntimeBundleVerifier() {}

    public static boolean verify(Path root) {
        return verify(root, BmfCpuProvider.RUNTIME_BUNDLE_WITH_SNDIO_SHA256)
                || verify(root, BmfCpuProvider.RUNTIME_BUNDLE_SHA256);
    }

    public static boolean verify(Path root, String requiredDigest) {
        if (root == null || !root.isAbsolute() || !root.equals(root.normalize())) {
            return false;
        }
        try {
            Path manifest = root.resolve("runtime-manifest.properties");
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            Properties metadata = new Properties();
            try (InputStream data = Files.newInputStream(manifest)) {
                metadata.load(data);
            }
            String recordedDigest = metadata.getProperty("bundle.sha256", "");
            if (!"0.2.0".equals(metadata.getProperty("bmf.version"))
                    || !"4.3.7".equals(metadata.getProperty("ffmpeg.version"))
                    || !"3.9.2".equals(metadata.getProperty("python.version"))
                    || !"1.19.5".equals(metadata.getProperty("numpy.version"))
                    || !BmfCpuProvider.SOURCE_ARCHIVE_SHA256.equals(metadata.getProperty("source.sha256"))
                    || !requiredDigest.equals(recordedDigest)
                    || !List.of(BmfCpuProvider.RUNTIME_BUNDLE_SHA256, BmfCpuProvider.RUNTIME_BUNDLE_WITH_SNDIO_SHA256).contains(requiredDigest)
                    || !Files.isExecutable(root.resolve("host-bundle/usr/bin/python3.9"))
                    || !insideBundle(root, root.resolve("output/bmf/lib/libbuiltin_modules.so"))) {
                return false;
            }
            return recordedDigest.equals(bundleDigest(root));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /**
     * A versioned distribution may expose shared libraries through in-bundle soname symlinks;
     * the bundle digest already covers each link target, so a link is accepted only when it
     * resolves to a regular file that stays inside the verified root.
     */
    private static boolean insideBundle(Path root, Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        if (!Files.isSymbolicLink(path)) {
            return true;
        }
        Path real = path.toRealPath();
        return real.startsWith(root.toRealPath());
    }

    static String bundleDigest(Path root) throws IOException {
        MessageDigest digest = sha256();
        for (String subtree : List.of("output/bmf", "host-bundle")) {
            try (var paths = Files.walk(root.resolve(subtree))) {
                for (Path path : paths.sorted(Comparator.comparing(
                        value -> root.relativize(value).toString().replace('\\', '/'))).toList()) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    String value;
                    if (Files.isSymbolicLink(path)) {
                        Path target = Files.readSymbolicLink(path);
                        if (target.isAbsolute()) throw new IOException("absolute runtime link");
                        value = "link:" + target;
                    } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        value = "file:" + fileSha256(path);
                    } else {
                        continue;
                    }
                    digest.update((relative + "\u0000" + value + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fileSha256(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream data = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int size; (size = data.read(buffer)) != -1; ) {
                digest.update(buffer, 0, size);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
