package com.example.platform.workerfabric.domain.providernative;

/** Typed runtime-mechanics representation; provider-specific variants may extend this root later. */
public sealed interface InvocationSpec
        permits ProcessInvocationSpec {

    InvocationKind kind();
}
