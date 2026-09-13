package com.example.platform.render.api.event;
/** Provider-neutral reasons for accepted unsuccessful Render outcomes. Native details remain diagnostics. */
public enum RenderFailureReason {
 INPUT_RESOLUTION_FAILED("Render input resolution failed"),
 EXECUTION_FAILED("Render execution failed"),
 OUTPUT_REJECTED("Render output acceptance failed"),
 COMMERCIAL_REJECTED("Render admission rejected"),
 STALE_TIMEOUT("Render job timed out");
 private final String description;
 RenderFailureReason(String description){this.description=description;}
 public String description(){return description;}
}
