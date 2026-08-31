package com.example.platform.studio.camera;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Provider-neutral authored imaging intent. Absence means no technical intent was authored. */
public final class CameraImagingIntent {
    private static final DecimalValue MAX_SENSOR_MM = DecimalValue.of("1000");
    private static final DecimalValue MAX_APERTURE = DecimalValue.of("128");
    private final Sensor sensor;
    private final Aperture aperture;
    private final FocusDistance focusDistance;
    private final Exposure exposure;

    private CameraImagingIntent(Sensor sensor, Aperture aperture, FocusDistance focusDistance, Exposure exposure) {
        this.sensor = sensor;
        this.aperture = aperture;
        this.focusDistance = focusDistance;
        this.exposure = exposure;
    }

    public static CameraImagingIntent none() {
        return new CameraImagingIntent(null, null, null, null);
    }

    public static CameraImagingIntent authored(
            Sensor sensor, Aperture aperture, FocusDistance focusDistance, Exposure exposure) {
        return new CameraImagingIntent(sensor, aperture, focusDistance, exposure);
    }

    public Optional<Sensor> sensor() { return Optional.ofNullable(sensor); }
    public Optional<Aperture> aperture() { return Optional.ofNullable(aperture); }
    public Optional<FocusDistance> focusDistance() { return Optional.ofNullable(focusDistance); }
    public Optional<Exposure> exposure() { return Optional.ofNullable(exposure); }
    public boolean isAuthored() { return sensor != null || aperture != null || focusDistance != null || exposure != null; }

    public String canonicalJson() {
        Map<String, String> values = new LinkedHashMap<>();
        if (aperture != null) values.put("aperture", aperture.canonicalJson());
        if (exposure != null) values.put("exposure", exposure.canonicalJson());
        if (focusDistance != null) values.put("focusDistance", focusDistance.canonicalJson());
        if (sensor != null) values.put("sensor", sensor.canonicalJson());
        return CanonicalJson.object(values);
    }

    public record Sensor(DecimalValue widthMillimeters, DecimalValue heightMillimeters) {
        public Sensor {
            if (!positiveAtMost(widthMillimeters, MAX_SENSOR_MM)
                    || !positiveAtMost(heightMillimeters, MAX_SENSOR_MM)) {
                throw new IllegalArgumentException("sensor dimensions must be positive and at most 1000 millimeters");
            }
        }
        String canonicalJson() {
            return CanonicalJson.object(Map.of(
                    "heightMillimeters", CanonicalJson.quote(heightMillimeters.canonical()),
                    "widthMillimeters", CanonicalJson.quote(widthMillimeters.canonical())));
        }
    }

    public record Aperture(DecimalValue fNumber) {
        public Aperture {
            if (!positiveAtMost(fNumber, MAX_APERTURE)) {
                throw new IllegalArgumentException("aperture f-number must be positive and at most 128");
            }
        }
        String canonicalJson() {
            return CanonicalJson.object(Map.of("fNumber", CanonicalJson.quote(fNumber.canonical())));
        }
    }

    public record FocusDistance(DecimalValue meters) {
        public FocusDistance {
            if (meters == null || !meters.isPositive()) {
                throw new IllegalArgumentException("focus distance must be positive meters");
            }
        }
        String canonicalJson() {
            return CanonicalJson.object(Map.of("meters", CanonicalJson.quote(meters.canonical())));
        }
    }

    public record Exposure(MediaTime shutterDuration, int iso) {
        public Exposure {
            if (shutterDuration == null || shutterDuration.equals(MediaTime.ZERO)) {
                throw new IllegalArgumentException("shutter duration must be positive");
            }
            if (iso < 1 || iso > 204800) {
                throw new IllegalArgumentException("ISO must be between 1 and 204800");
            }
        }
        String canonicalJson() {
            return CanonicalJson.object(Map.of(
                    "iso", Integer.toString(iso),
                    "shutterDuration", CanonicalJson.object(Map.of(
                            "ticks", Long.toString(shutterDuration.ticks()),
                            "timeScale", Long.toString(shutterDuration.timeScale())))));
        }
    }

    private static boolean positiveAtMost(DecimalValue value, DecimalValue maximum) {
        return value != null && value.isPositive() && value.compareTo(maximum) <= 0;
    }
}
