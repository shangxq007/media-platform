package com.example.platform.providerplugin.remote;

import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.workerfabric.domain.providernative.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Wire-only tags for existing sealed runtime types; no tags or compatibility aliases in domain models. */
public final class WorkerInvocationCodec {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new Jdk8Module())
            .addMixIn(InvocationSpec.class, InvocationTag.class)
            .addMixIn(ProviderCapabilityProfileVersionOrDigest.class, ProfileTag.class);
    private WorkerInvocationCodec() {}
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = ProcessInvocationSpec.class, name = "PROCESS"))
    interface InvocationTag {}
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = ProviderCapabilityProfileVersionOrDigest.VersionReference.class, name = "VERSION"),
            @JsonSubTypes.Type(value = ProviderCapabilityProfileVersionOrDigest.DigestReference.class, name = "DIGEST")})
    interface ProfileTag {}
    public static byte[] encode(Object value) throws IOException { return JSON.writeValueAsBytes(value); }
    public static <T> T decode(byte[] value, Class<T> type) throws IOException { return JSON.readValue(value, type); }
    public static RuntimeExecutionContext context(RuntimeExecutionBundle b) {
        return new RuntimeExecutionContext(b.executableTaskId(), b.providerBindingPin(),
                b.platformExecutionAttemptId(), b.platformOwnershipGeneration());
    }
    /** Correlation checksum of the exact transport context; not a domain semantic digest. */
    public static String correlation(RuntimeExecutionContext context) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(context))); }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot encode execution identity", failure); }
    }
}
