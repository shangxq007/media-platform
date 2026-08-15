package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI24): typed scan description — source truth. Deinterlace
 * algorithm/policy is future Operation/RenderPlan concern (never execution
 * flags here).
 */
public sealed interface ScanDescription permits
        ScanDescription.Progressive,
        ScanDescription.Interlaced {

    record Progressive() implements ScanDescription {
    }

    record Interlaced(FieldOrder fieldOrder) implements ScanDescription {
        public Interlaced {
            if (fieldOrder == null) {
                throw new IllegalArgumentException("field order required for interlaced scan");
            }
        }
    }

    enum FieldOrder {
        TOP_FIELD_FIRST, BOTTOM_FIELD_FIRST
    }
}
