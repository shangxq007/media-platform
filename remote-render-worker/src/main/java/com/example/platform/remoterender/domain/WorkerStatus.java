package com.example.platform.remoterender.domain;

/**
 * Status of a remote render worker.
 */
public enum WorkerStatus {
    IDLE,
    BUSY,
    OFFLINE,
    ERROR
}
