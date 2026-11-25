package com.example.platform.ffmpeg;

import java.nio.file.*;
import java.util.*;

/** Read-only assets of the configured runtime distribution; never selected from request data. */
final class FfmpegRuntimeFiles {
    static final String DISTRIBUTION_ID = "bytedeco-ffmpeg-7.1.1-1.5.12-linux-x86_64-gpl";
    private static final Map<String, String> DISTRIBUTION = Map.ofEntries(
            Map.entry("ffmpeg", "71dc890db7bad596422ad770ebbb48cad6fd979f3a8be5070681c3fee57d3ac2"),
            Map.entry("libavcodec.so.61", "d20fbcf6f37ad54f263c701fafea3b1b472a70817fa17fb2538285891cec006b"),
            Map.entry("libavdevice.so.61", "b6cbdc9668514ed8b229e9ea3229423ca61f4161dbf30042c2a1d5708c2daebb"),
            Map.entry("libavfilter.so.10", "b4fe019d4c826cbb231e1ef3f10529a296ee3820e534e3dba44afa915f19bb62"),
            Map.entry("libavformat.so.61", "e169f54a9b9c8561852c65e87ce60fcdc3cdea225c20f5f6273462cf1fb19603"),
            Map.entry("libavutil.so.59", "26d1e91d4ea4f5c7ae59556712364b1c8ded1ad009733e2ed2d436c85c394be2"),
            Map.entry("libdrm.so.2", "bab1e283c0fdd19eae10db2522fdc5806f0efee3c175e5e8bb1bc1b478c7e2b1"),
            Map.entry("libpostproc.so.58", "581e4b870449c2aa7426a15f98c19baae66439184fffc964d51ca283bdbcfa46"),
            Map.entry("libswresample.so.5", "d075f6d5c90ef4723a93af5e2fb21783f165c50d3533b43af31be6ee4b627603"),
            Map.entry("libswscale.so.8", "df61cfa054fdf29f2942f940f129f4a17bc9bfec1ade32e11b721aafd0a3743d"),
            Map.entry("libva-drm.so", "9cf5b3c1a3f86222272ed27f68c7bfcb8d1850d8b2cc9a4037d8298583420b99"),
            Map.entry("libva.so", "7ee0c548a2ce345a74b482aef98a3ddcb825504db7f1cda75020f9f999fef1c1"));
    private static final String SUBTITLE_FONT_SHA256 = "2745e9681cb9d8a5c8901b62c9e1bd98c9c774365fc3b84dd467621013b51cd3";

    private FfmpegRuntimeFiles() {}

    static boolean matchesDistribution(Path executable, Path font) {
        if (!executable.getFileName().toString().equals("ffmpeg") || executable.getParent() == null) return false;
        for (var item : DISTRIBUTION.entrySet()) {
            if (!matches(executable.resolveSibling(item.getKey()), item.getValue())) return false;
        }
        return font == null || matches(font, SUBTITLE_FONT_SHA256);
    }

    private static boolean matches(Path file, String expected) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var data = Files.newInputStream(file)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            data.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
            return expected.equals(HexFormat.of().formatHex(digest.digest()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException unavailable) {
            return false;
        }
    }
    static Set<Path> inputs(Path executable, Path... mediaInputs) {
        Set<Path> inputs = new HashSet<>(List.of(mediaInputs));
        Path root = executable.getParent();
        // The pinned distribution uses $ORIGIN DSOs. Keep that dedicated directory together;
        // binding just its executable under another name incorrectly loads host codec libraries.
        if (root != null && Files.isRegularFile(root.resolve("libavcodec.so.61"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root.resolve("libavformat.so.61"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root.resolve("libavutil.so.59"), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("runtime directory symlink refused");
            inputs.add(root);
        } else {
            inputs.add(executable);
        }
        return Set.copyOf(inputs);
    }
}
