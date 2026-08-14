package com.example.platform.media.infrastructure.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.probe.MediaProbeNormalizer;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.media.domain.stream.StreamKind;
import com.example.platform.media.domain.description.SourceAudioDescription;
import com.example.platform.media.domain.description.SourceColorDescription;
import com.example.platform.media.domain.description.SourceVideoDescription;
import com.example.platform.media.domain.time.TimeBase;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * INGEST_NORMALIZATION_BOUNDARY_V1 — single normalization implementation.
 *
 * <p>Parses a provider ffprobe-style raw observation into the canonical
 * source structural model. All time/rate values become EXACT rationals from
 * the observation's own decimal/fraction strings — no double round-trip.
 * Unknown values are absent; sentinel values are rejected.
 */
@Component
public class FfprobeMediaProbeNormalizer implements MediaProbeNormalizer {

    private static final Pattern RATIONAL = Pattern.compile("^(-?\\d+)\\s*/\\s*(\\d+)$");
    private static final Pattern DECIMAL = Pattern.compile("^(-?\\d+)(?:\\.(\\d+))?$");

    @Override
    public NormalizedMediaProbe normalize(MediaProbeObservation observation, MediaAssetId mediaAssetId) {
        if (observation == null || !observation.valid()) {
            return new NormalizedMediaProbe(mediaAssetId, null, null, false,
                    observation != null && observation.clientExportCompatible(),
                    true, List.of());
        }
        RawProbe parsed = parse(observation.rawPayload());
        MediaTime duration = parsed.durationSeconds != null
                ? MediaTime.ofRational(parsed.durationSeconds[0], parsed.durationSeconds[1])
                : null;
        List<MediaStream> streams = new ArrayList<>();
        for (RawStream s : parsed.streams) {
            StreamKind kind = mapKind(s.codecType);
            FrameRate rate = s.frameRate != null ? FrameRate.of(s.frameRate[0], s.frameRate[1]) : null;
            TimeBase tb = s.timeBase != null ? TimeBase.of(s.timeBase[0], s.timeBase[1]) : TimeBase.of(1, 1);
            SourceVideoDescription video = kind == StreamKind.VIDEO
                    ? new SourceVideoDescription(s.width, s.height, s.pixelFormat, null) : null;
            SourceAudioDescription audio = kind == StreamKind.AUDIO
                    ? new SourceAudioDescription(s.sampleRate, s.channels, s.channelLayout, s.sampleFormat, null) : null;
            SourceColorDescription color = new SourceColorDescription(
                    s.colorPrimaries, s.colorTransfer, s.colorMatrix, s.colorRange, null, null);
            streams.add(new MediaStream(
                    MediaStreamId.of(mediaAssetId.value() + ":s" + s.index),
                    s.index, kind, s.codecName, tb, rate, s.isVfr,
                    video, audio, color, s.codecType));
        }
        return new NormalizedMediaProbe(mediaAssetId, duration, parsed.container,
                parsed.isVfr, observation.clientExportCompatible(),
                observation.normalizeRequired(), streams);
    }

    private record RawProbe(String container, boolean isVfr, long[] durationSeconds, List<RawStream> streams) {}

    private record RawStream(int index, String codecType, String codecName, Integer width, Integer height,
                             String pixelFormat, long[] frameRate, long[] timeBase,
                             Integer sampleRate, Integer channels, String channelLayout, String sampleFormat,
                             boolean isVfr, String colorPrimaries, String colorTransfer, String colorMatrix,
                             String colorRange) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RawProbe parse(String raw) {
        String container = null;
        boolean isVfr = false;
        long[] duration = null;
        List<RawStream> streams = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new RawProbe(null, false, null, streams);
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = MAPPER.readTree(trimmed);
                JsonNode fmt = root.path("format");
                if (!fmt.isMissingNode()) {
                    container = textOrNull(fmt, "format_name");
                    String dur = textOrNull(fmt, "duration");
                    if (dur != null) {
                        duration = decimalToRational(dur);
                    }
                }
                JsonNode arr = root.path("streams");
                if (arr.isArray()) {
                    int i = 0;
                    for (JsonNode s : arr) {
                        String codecType = textOrNull(s, "codec_type");
                        long[] rate = null;
                        String rfr = textOrNull(s, "r_frame_rate");
                        if (rfr != null && !"0/0".equals(rfr)) {
                            Matcher m = RATIONAL.matcher(rfr.trim());
                            if (m.matches()) {
                                rate = new long[]{Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
                            }
                        }
                        long[] timeBase = null;
                        String tb = textOrNull(s, "time_base");
                        if (tb != null) {
                            Matcher m = RATIONAL.matcher(tb.trim());
                            if (m.matches()) {
                                timeBase = new long[]{Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
                            }
                        }
                        Integer sampleRate = null;
                        String sr = textOrNull(s, "sample_rate");
                        if (sr != null) {
                            try {
                                sampleRate = Integer.parseInt(sr.trim());
                            } catch (NumberFormatException ignored) {
                                sampleRate = null;
                            }
                        }
                        boolean vfr = "0/0".equals(textOrNull(s, "avg_frame_rate"))
                                && "0/0".equals(textOrNull(s, "r_frame_rate"));
                        streams.add(new RawStream(i, codecType, textOrNull(s, "codec_name"),
                                s.hasNonNull("width") ? s.get("width").asInt() : null,
                                s.hasNonNull("height") ? s.get("height").asInt() : null,
                                textOrNull(s, "pix_fmt"), rate, timeBase, sampleRate,
                                s.hasNonNull("channels") ? s.get("channels").asInt() : null,
                                textOrNull(s, "channel_layout"), textOrNull(s, "sample_fmt"),
                                vfr, textOrNull(s, "color_primaries"),
                                textOrNull(s, "color_transfer"),
                                textOrNull(s, "color_space"),
                                textOrNull(s, "color_range")));
                        i++;
                    }
                }
            } catch (Exception e) {
                return new RawProbe(null, false, null, List.of());
            }
        }
        return new RawProbe(container, isVfr, duration, streams);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static long[] decimalToRational(String decimal) {
        Matcher m = DECIMAL.matcher(decimal.trim());
        if (!m.matches()) {
            return null;
        }
        String intPart = m.group(1);
        String fracPart = m.group(2);
        if (fracPart == null || fracPart.isEmpty()) {
            return new long[]{Long.parseLong(intPart), 1L};
        }
        long den = 1;
        for (int i = 0; i < fracPart.length(); i++) {
            den *= 10;
        }
        long num = Long.parseLong(intPart) * den + Long.parseLong(fracPart);
        long g = gcd(Math.abs(num), den);
        return new long[]{num / g, den / g};
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static StreamKind mapKind(String codecType) {
        if (codecType == null) {
            return StreamKind.DATA;
        }
        return switch (codecType.toLowerCase()) {
            case "video" -> StreamKind.VIDEO;
            case "audio" -> StreamKind.AUDIO;
            case "subtitle" -> StreamKind.SUBTITLE;
            default -> StreamKind.DATA;
        };
    }
}
