package com.example.platform.workerfabric.domain;

/**
 * Fail-closed signal: the local host's resource evidence could not be observed.
 *
 * <p>Raised instead of returning a defaulted or synthetic capacity/usage value. The bounded V1
 * single-host configuration is only valid when the host evidence is real; a caller that sees this
 * exception must not register a host snapshot.
 */
public class HostObservationUnavailableException extends RuntimeException {

    public HostObservationUnavailableException(String message) {
        super(message);
    }

    public HostObservationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
