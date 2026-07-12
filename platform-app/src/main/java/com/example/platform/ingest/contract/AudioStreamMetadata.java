package com.example.platform.ingest.contract;

public record AudioStreamMetadata(
    String codec,
    Integer sampleRate,
    Integer channels,
    Long bitrate,
    Integer streamIndex
) {}
