package com.example.platform.workerfabric.domain;

/** Frozen V1 remote-provider interaction mechanics. */
public enum RemoteProviderInteractionMode {
    SYNCHRONOUS,
    ASYNC_CALLBACK,
    ASYNC_POLL,
    ASYNC_STREAM
}
