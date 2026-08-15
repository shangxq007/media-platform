package com.example.platform.fonttext.security;

/** ROADMAP_19 (C6/C13): font security lifecycle state. Raw font NEVER enters resolver/shaper. */
public enum FontSecurityState {
    RAW,
    STRUCTURALLY_VALIDATED,
    SANITIZED,
    CONFORMANCE_EVALUATED,
    VALIDATED_EXECUTION_FONT
}
