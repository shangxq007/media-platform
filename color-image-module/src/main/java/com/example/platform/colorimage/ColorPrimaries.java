package com.example.platform.colorimage;

/**
 * ROADMAP_18 (CI10): typed well-known color primaries + bounded exact custom
 * chromaticity path. Unknown provider values never become arbitrary strings.
 */
public sealed interface ColorPrimaries {

    /** Well-known registry values (typed, code-owned). */
    enum WellKnown implements ColorPrimaries {
        BT709, BT2020, DCI_P3, DISPLAY_P3, SMPTE_C, NTSC1953, EBU3213, GENERIC_FILM,
        /** provider reported an unsupported/unknown id — typed UNKNOWN, not a string. */
        UNKNOWN
    }

    /** Bounded exact custom primaries with canonical chromaticities. */
    record Custom(Chromaticity red, Chromaticity green, Chromaticity blue, Chromaticity whitePoint)
            implements ColorPrimaries {
        public Custom {
            if (red == null || green == null || blue == null || whitePoint == null) {
                throw new IllegalArgumentException("all custom chromaticities required");
            }
        }
    }
}
