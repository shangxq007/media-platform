package com.example.platform.shared.asset;

import java.util.Map;

/** A single inbound reference to a storage URI from another subsystem. */
public record StorageUriReferenceHit(String kind, String entityId, String message, Map<String, String> details) {}
