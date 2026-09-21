package com.example.platform.render.api;
import java.util.Map;
/** Internal global diagnostics. Exposing these counters requires platform-admin authority; never use them for lifecycle decisions. */
public interface RenderOperationalQuery {
    Map<String, Long> exportSessionCounts();
    Map<String, Long> renderJobCounts();
}
