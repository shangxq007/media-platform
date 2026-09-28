/**
 * Canonical admission boundary for platform execution plans.
 *
 * <p>ROADMAP_22 / server-owned admission: this package owns the admission port and the
 * provider-bound execution plan payload submitted for admission. Exposed to consumers that
 * submit or observe admission (e.g. composition).
 */
@org.springframework.modulith.NamedInterface("admission")
package com.example.platform.execution.admission;
