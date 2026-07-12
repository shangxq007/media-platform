package com.example.platform.ingest.contract;

import java.math.BigDecimal;

public record VideoStreamMetadata(
    String codec,
    Integer width,
    Integer height,
    BigDecimal frameRate,
    Long bitrate,
    String pixelFormat,
    String colorSpace,
    Integer rotation,
    Integer streamIndex
) {}
