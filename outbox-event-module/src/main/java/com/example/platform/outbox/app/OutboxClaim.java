package com.example.platform.outbox.app;
/** A single durable processing claim. The token changes on every acquisition. */
public record OutboxClaim(String eventId,String token) {
    public OutboxClaim {
        if(eventId==null || eventId.isBlank() || token==null || token.isBlank())
            throw new IllegalArgumentException("Outbox claim identity required");
    }
}
