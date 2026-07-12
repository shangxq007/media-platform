package com.example.platform.ingest.contract;

import java.math.BigDecimal;
import java.util.List;

public record MediaTechnicalMetadata(
    MediaCategory mediaCategory,
    Long durationMs,
    String containerFormat,
    String formatLongName,
    Long bitrate,
    Long sizeBytes,
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
    String pixelFormat,
    String colorSpace,
    List<VideoStreamMetadata> videoStreams,
    List<AudioStreamMetadata> audioStreams,
    List<SubtitleStreamMetadata> subtitleStreams,
    MediaProbeSummary probe
) {}
