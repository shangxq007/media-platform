/**
 * Worker-local artifact reuse seam (materialization port, direct storage materializer, bounded cache).
 *
 * <p>Exposed as the worker-fabric {@code runtime} named interface, consistent with the class-level
 * {@code @NamedInterface("runtime")} already declared in this package
 * (COVER-PROVIDER-MODULITH-001).
 */
@org.springframework.modulith.NamedInterface("runtime")
package com.example.platform.workerfabric.reuse;
