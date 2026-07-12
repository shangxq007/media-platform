package com.example.platform.ingest.contract;

public record SubtitleStreamMetadata(
    String codec,
    String language,
    Integer streamIndex
) {}
