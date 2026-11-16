/**
 * Worker-local artifact reuse seam (materialization, direct storage materialization, bounded cache).
 *
 * <p>Exposed as the narrow {@code reuse} named interface: the worker composition (API/WORKER roles
 * and the provider-plugin host) consumes this artifact-reuse seam; worker-fabric internals outside
 * the seam stay non-exposed (COVER-PROVIDER-MODULITH-001 / MODULITH-NARROW-001).
 */
@org.springframework.modulith.NamedInterface("reuse")
package com.example.platform.workerfabric.reuse;
