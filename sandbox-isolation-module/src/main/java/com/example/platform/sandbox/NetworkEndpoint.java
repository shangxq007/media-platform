package com.example.platform.sandbox;

import java.util.Locale;
import java.util.Objects;

@org.springframework.modulith.NamedInterface("API")
public record NetworkEndpoint(Protocol protocol, String host, int port) {
    @org.springframework.modulith.NamedInterface("API")
    public enum Protocol { TCP, UDP }

    public NetworkEndpoint {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(host, "host");
        host = host.toLowerCase(Locale.ROOT);
        if (host.isBlank() || host.equals("*") || port < 1 || port > 65535) {
            throw new IllegalArgumentException("endpoint must have an exact host and valid port");
        }
    }

    public static NetworkEndpoint tcp(String host, int port) {
        return new NetworkEndpoint(Protocol.TCP, host, port);
    }
}
