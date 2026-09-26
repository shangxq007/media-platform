/**
 * Worker-local artifact reuse seam (materialization, direct storage materialization, bounded cache).
 *
 * <p>This package is deliberately <em>not</em> exposed as a whole: the worker-fabric {@code runtime}
 * named interface is declared per consumed type, so only the artifact-reuse seam an external consumer
 * actually needs becomes part of the module's public surface
 * (COVER-PROVIDER-MODULITH-001 / MODULITH-NARROW-001).
 */
package com.example.platform.workerfabric.reuse;
