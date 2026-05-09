package com.example.platform.render.domain.timeline;

/**
 * Placeholder for OpenTimelineIO interchange format adapter.
 *
 * <p><strong>OTIO is an interchange format, not a renderer.</strong> This adapter
 * provides conversion between the platform's internal {@link TimelineSpec} model
 * and the OpenTimelineIO JSON format.</p>
 *
 * <h3>Future Implementation</h3>
 * <p>When OTIO Java bindings become available, this adapter will:</p>
 * <ul>
 *   <li>Serialize {@link TimelineSpec} to OTIO JSON for export</li>
 *   <li>Deserialize OTIO JSON to {@link TimelineSpec} for import</li>
 *   <li>Handle OTIO-specific features (markers, metadata, effects)</li>
 * </ul>
 *
 * <h3>Current Status</h3>
 * <p>This is a placeholder. The internal {@link TimelineSpec} model is the
 * canonical representation. OTIO conversion is deferred until the OTIO Java
 * library is integrated.</p>
 *
 * @see TimelineSpec
 * @see <a href="https://opentimelineio.readthedocs.io/">OpenTimelineIO Documentation</a>
 */
public final class OpenTimelineioAdapter {

    private OpenTimelineioAdapter() {
        // utility class
    }

    /**
     * Converts a {@link TimelineSpec} to OTIO JSON format.
     *
     * @param timeline the timeline to convert
     * @return OTIO JSON string
     * @throws UnsupportedOperationException always (placeholder)
     */
    public static String toOtioJson(TimelineSpec timeline) {
        throw new UnsupportedOperationException(
                "OTIO export is not yet implemented. Use TimelineSpec as the canonical model.");
    }

    /**
     * Converts OTIO JSON to a {@link TimelineSpec}.
     *
     * @param otioJson the OTIO JSON string
     * @return the parsed timeline specification
     * @throws UnsupportedOperationException always (placeholder)
     */
    public static TimelineSpec fromOtioJson(String otioJson) {
        throw new UnsupportedOperationException(
                "OTIO import is not yet implemented. Use TimelineSpec as the canonical model.");
    }
}
