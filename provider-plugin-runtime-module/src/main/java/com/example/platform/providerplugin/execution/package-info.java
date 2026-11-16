/**
 * Provider-runtime execution backend registry and the generic local-process backend.
 *
 * <p>Exposed as the {@code execution} named interface so the composition root can assemble the
 * backend list without the host knowing any concrete media provider.
 */
@org.springframework.modulith.NamedInterface("execution")
package com.example.platform.providerplugin.execution;
