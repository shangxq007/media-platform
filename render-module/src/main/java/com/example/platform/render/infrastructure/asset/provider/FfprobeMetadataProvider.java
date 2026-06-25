package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.render.domain.asset.semantic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FfprobeMetadataProvider implements SemanticMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(FfprobeMetadataProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public boolean supports(SemanticMetadataRequest request) {
        return request.storageUri() != null && !request.storageUri().isBlank();
    }

    @Override
    public SemanticMetadataResult analyze(SemanticMetadataRequest request) {
        try {
            ProbeMetadata probe = probeFile(request.storageUri());
            if (!probe.isValid()) {
                return SemanticMetadataResult.failure(providerName(),
                        "FFprobe failed: " + (probe.errorMessage() != null ? probe.errorMessage() : "no valid data"));
            }
            AssetSemanticMetadata meta = AssetSemanticMetadata.empty(request.assetId(), request.assetVersion());
            return SemanticMetadataResult.success(meta, providerName(), Map.of("probe", probe));
        } catch (Exception e) {
            log.error("FFprobe fatal error for asset={}: {}", request.assetId(), e.getMessage());
            return SemanticMetadataResult.failure(providerName(), e.getMessage());
        }
    }

    @Override
    public String providerName() { return "ffprobe"; }

    @Override
    public SemanticCapability capability() { return SemanticCapability.PROBE; }

    ProbeMetadata probeFile(String path) {
        try {
            List<String> cmd = List.of("ffprobe", "-v", "quiet", "-print_format", "json",
                    "-show_format", "-show_streams", path);
            ProcessResult result = executeWithTimeout(cmd, TIMEOUT_SECONDS);
            if (!result.success) {
                return ProbeMetadata.empty().withError("FFPROBE_MISSING_OR_TIMEOUT",
                        result.stderr != null ? result.stderr : "ffprobe command failed with exit code " + result.exitCode);
            }
            return parseFromJson(result.stdout);
        } catch (Exception e) {
            return ProbeMetadata.empty().withError("FFPROBE_EXCEPTION", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ProbeMetadata parseFromJson(String json) {
        try {
            var root = MAPPER.readValue(json, Map.class);
            var fmt = (Map<String, Object>) root.getOrDefault("format", Map.of());
            var streams = (List<Map<String, Object>>) root.getOrDefault("streams", List.of());

            double duration = dp(fmt.getOrDefault("duration", "0")),
                   size = lp(fmt.getOrDefault("size", "0"));
            int videoBitrate = ip(fmt.getOrDefault("bit_rate", "0")) / 1000;

            int width = 0, height = 0;
            double fps = 0, frameCount = 0;
            String vCodec = null, vProfile = null, pFormat = null, cSpace = null, cRange = null;
            int aChannels = 0, aRate = 0, aBitrate = 0;
            String aCodec = null, aLayout = null;
            int aDepth = 0;
            List<ProbeMetadata.StreamSummary> summaries = new ArrayList<>();

            for (var s : streams) {
                String ct = (String) s.getOrDefault("codec_type", "");
                summaries.add(new ProbeMetadata.StreamSummary(ct,
                        (String) s.get("codec_name"), ip(s.getOrDefault("width", "0")),
                        ip(s.getOrDefault("height", "0")),
                        parseFps((String) s.get("r_frame_rate")),
                        ip(s.getOrDefault("channels", "0")),
                        ip(s.getOrDefault("sample_rate", "0"))));
                switch (ct) {
                    case "video" -> {
                        width = ip(s.getOrDefault("width", "0"));
                        height = ip(s.getOrDefault("height", "0"));
                        vCodec = (String) s.get("codec_name");
                        vProfile = (String) s.get("profile");
                        pFormat = (String) s.get("pix_fmt");
                        cSpace = (String) s.get("color_space");
                        cRange = (String) s.get("color_range");
                        fps = parseFps((String) s.get("r_frame_rate"));
                        frameCount = dp(s.getOrDefault("nb_frames", s.getOrDefault("nb_read_frames", "0")));
                    }
                    case "audio" -> {
                        aChannels = ip(s.getOrDefault("channels", "0"));
                        aRate = ip(s.getOrDefault("sample_rate", "0"));
                        aCodec = (String) s.get("codec_name");
                        aLayout = (String) s.get("channel_layout");
                        aDepth = ip(s.getOrDefault("bits_per_raw_sample",
                                s.getOrDefault("bits_per_sample", "0")));
                    }
                }
            }

            return new ProbeMetadata((String) fmt.get("format_name"), (long) size, duration,
                    fps, frameCount, width, height, vCodec, vProfile, pFormat, cSpace, cRange,
                    videoBitrate, aChannels, aLayout, aRate, aBitrate, aCodec, aDepth,
                    streams.size(), summaries, null, null);
        } catch (Exception e) {
            return ProbeMetadata.empty().withError("JSON_PARSE_ERROR", e.getMessage());
        }
    }

    private ProcessResult executeWithTimeout(List<String> cmd, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        String stdout, stderr;
        try (var in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
             var err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
            stdout = in.lines().reduce("", (a, b) -> a + b + "\n");
            stderr = err.lines().reduce("", (a, b) -> a + b + "\n");
        }

        boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return new ProcessResult(false, -1, "", "Timeout after " + timeoutSec + "s");
        }
        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            return new ProcessResult(false, exitCode, stdout, stderr);
        }
        return new ProcessResult(true, exitCode, stdout, stderr);
    }

    private record ProcessResult(boolean success, int exitCode, String stdout, String stderr) {}

    private static double dp(Object v) { try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; } }
    private static long lp(Object v) { try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; } }
    private static int ip(Object v) { try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; } }

    private static double parseFps(String rFrameRate) {
        if (rFrameRate == null || !rFrameRate.contains("/")) {
            try { return Double.parseDouble(rFrameRate); } catch (Exception e) { return 0; }
        }
        String[] parts = rFrameRate.split("/");
        try { return Integer.parseInt(parts[1]) == 0 ? 0 : (double) Integer.parseInt(parts[0]) / Integer.parseInt(parts[1]); }
        catch (Exception e) { return 0; }
    }
}
