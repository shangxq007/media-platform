package com.example.platform.render.domain.renderplan;

/**
 * Bounded V1 render node kinds (C3/C31). Sealed — new kinds are new permits, never
 * arbitrary strings. Each variant is a singleton record whose canonical name()
 * (e.g. "SOURCE", "DECODE") is its toString().
 */
public sealed interface RenderNodeKind permits
        RenderNodeKind.Source,
        RenderNodeKind.Decode,
        RenderNodeKind.Transform,
        RenderNodeKind.Effect,
        RenderNodeKind.Transition,
        RenderNodeKind.AudioProcess,
        RenderNodeKind.AudioMix,
        RenderNodeKind.TimedText,
        RenderNodeKind.Composite,
        RenderNodeKind.ColorTransform,
        RenderNodeKind.Mux,
        RenderNodeKind.Output {

    /** Canonical name for identity/fingerprinting. */
    String canonicalName();

    record Source() implements RenderNodeKind {
        @Override public String canonicalName() { return "SOURCE"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Decode() implements RenderNodeKind {
        @Override public String canonicalName() { return "DECODE"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Transform() implements RenderNodeKind {
        @Override public String canonicalName() { return "TRANSFORM"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Effect() implements RenderNodeKind {
        @Override public String canonicalName() { return "EFFECT"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Transition() implements RenderNodeKind {
        @Override public String canonicalName() { return "TRANSITION"; }
        @Override public String toString() { return canonicalName(); }
    }

    record AudioProcess() implements RenderNodeKind {
        @Override public String canonicalName() { return "AUDIO_PROCESS"; }
        @Override public String toString() { return canonicalName(); }
    }

    record AudioMix() implements RenderNodeKind {
        @Override public String canonicalName() { return "AUDIO_MIX"; }
        @Override public String toString() { return canonicalName(); }
    }

    record TimedText() implements RenderNodeKind {
        @Override public String canonicalName() { return "TIMED_TEXT"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Composite() implements RenderNodeKind {
        @Override public String canonicalName() { return "COMPOSITE"; }
        @Override public String toString() { return canonicalName(); }
    }

    record ColorTransform() implements RenderNodeKind {
        @Override public String canonicalName() { return "COLOR_TRANSFORM"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Mux() implements RenderNodeKind {
        @Override public String canonicalName() { return "MUX"; }
        @Override public String toString() { return canonicalName(); }
    }

    record Output() implements RenderNodeKind {
        @Override public String canonicalName() { return "OUTPUT"; }
        @Override public String toString() { return canonicalName(); }
    }
}
