package com.example.platform.render.domain.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

/**
 * Canonical Timeline Clip - stable entity with clipId.
 */
public class TimelineClip {
    
    @JsonProperty("clipId")
    private final String clipId;
    
    @JsonProperty("assetId")
    private final String assetId;
    
    @JsonProperty("startTime")
    private final Duration startTime;
    
    @JsonProperty("endTime")
    private final Duration endTime;
    
    @JsonProperty("trimStart")
    private final Duration trimStart;
    
    @JsonProperty("trimEnd")
    private final Duration trimEnd;

    @JsonCreator
    public TimelineClip(
            @JsonProperty("clipId") String clipId,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("startTime") Duration startTime,
            @JsonProperty("endTime") Duration endTime,
            @JsonProperty("trimStart") Duration trimStart,
            @JsonProperty("trimEnd") Duration trimEnd) {
        if (clipId == null || clipId.isBlank()) {
            throw new IllegalArgumentException("clipId must not be blank");
        }
        this.clipId = clipId;
        this.assetId = assetId;
        this.startTime = startTime != null ? startTime : Duration.ZERO;
        this.endTime = endTime != null ? endTime : Duration.ZERO;
        this.trimStart = trimStart != null ? trimStart : Duration.ZERO;
        this.trimEnd = trimEnd != null ? trimEnd : Duration.ZERO;
    }

    public String getClipId() { return clipId; }
    public String getAssetId() { return assetId; }
    public Duration getStartTime() { return startTime; }
    public Duration getEndTime() { return endTime; }
    public Duration getTrimStart() { return trimStart; }
    public Duration getTrimEnd() { return trimEnd; }
}
