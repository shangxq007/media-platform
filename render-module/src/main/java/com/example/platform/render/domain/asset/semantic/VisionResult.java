package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * Vision result produced by a computer vision provider (YOLO, etc).
 */
public record VisionResult(
        String provider,
        String model,
        double processingTimeSec,
        List<DetectedFrame> frames,
        List<VisionObject> objects,
        List<VisionScene> scenes,
        List<VisionBrand> brands,
        List<VisionPerson> people) {

    public record DetectedFrame(int frameNum, long timeMs, List<VisionObject> objects) {}
    public record VisionObject(String label, double confidence, long timeMs) {}
    public record VisionScene(long startMs, long endMs, String label, double confidence) {}
    public record VisionBrand(String brandName, double confidence, long timeMs) {}
    public record VisionPerson(String name, double confidence, long timeMs) {}
}
