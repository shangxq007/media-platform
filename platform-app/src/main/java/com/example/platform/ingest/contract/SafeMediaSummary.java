package com.example.platform.ingest.contract;

import java.math.BigDecimal;

public record SafeMediaSummary(
    Long durationMs,
    String containerFormat,
    Long bitrate,
    boolean hasVideo,
    boolean hasAudio,
    boolean hasSubtitle,
    int videoStreamCount,
    int audioStreamCount,
    int subtitleStreamCount,
    String primaryVideoCodec,
    String primaryAudioCodec,
    Integer width,
    Integer height,
    BigDecimal frameRate,
    Integer sampleRate,
    Integer channels,
    Integer rotation,
    MediaProbeStatus probeStatus
) {}
