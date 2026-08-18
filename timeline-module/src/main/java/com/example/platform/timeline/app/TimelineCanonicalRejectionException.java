package com.example.platform.timeline.app;

import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.canonicalmodel.TimelineModelPath;
import java.util.List;
import java.util.Objects;

/**
 * Frozen invalid-result contract (NDSF-SCOPE-E1 F015): unchecked rejection exception
 * carrying ordered, immutable diagnostics. Precedent: TimelineConflictException (unchecked).
 * <p>
 * Canonical failures carry {@link TimelineDiagnostic} verbatim (codes, severities, model
 * paths, deterministic ordering). Adapter-level structural failures carry
 * {@link AdapterDiagnostic} using exactly the five frozen application-level codes.
 */
public class TimelineCanonicalRejectionException extends RuntimeException {

    /** Exactly the five frozen application-level adapter rejection codes (F015). */
    public enum Code {
        TIMELINE_SCHEMA_UNSUPPORTED,
        TIMELINE_TRACK_TYPE_UNSUPPORTED,
        TIMELINE_CLIP_ID_INVALID,
        TIMELINE_SOURCE_REF_INVALID,
        TIMELINE_TIMING_INVALID,
        TIMELINE_EFFECT_KEY_INVALID
    }

    /** Application-level adapter diagnostic: stable code + model path + human message. */
    public record AdapterDiagnostic(Code code, TimelineModelPath path, String message) {
        public AdapterDiagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(path, "path");
            message = message == null ? code.name() : message;
        }
    }

    private final List<TimelineDiagnostic> diagnostics;
    private final List<AdapterDiagnostic> adapterDiagnostics;

    public TimelineCanonicalRejectionException(List<TimelineDiagnostic> diagnostics) {
        super("Timeline canonical validation rejected the save");
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.adapterDiagnostics = List.of();
    }

    public TimelineCanonicalRejectionException(AdapterDiagnostic adapterDiagnostic) {
        super(adapterDiagnostic == null ? "Timeline canonical rejection" : adapterDiagnostic.message());
        this.diagnostics = List.of();
        this.adapterDiagnostics = adapterDiagnostic == null ? List.of() : List.of(adapterDiagnostic);
    }

    /** Ordered canonical diagnostics (verbatim, deterministic validator ordering). */
    public List<TimelineDiagnostic> diagnostics() {
        return diagnostics;
    }

    /** Ordered application-level adapter diagnostics (frozen five codes). */
    public List<AdapterDiagnostic> adapterDiagnostics() {
        return adapterDiagnostics;
    }

    public boolean hasCanonicalDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public boolean hasAdapterDiagnostics() {
        return !adapterDiagnostics.isEmpty();
    }
}
