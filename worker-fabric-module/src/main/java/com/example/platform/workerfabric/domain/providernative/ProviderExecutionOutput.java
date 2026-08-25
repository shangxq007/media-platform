package com.example.platform.workerfabric.domain.providernative;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Provider-produced bytes before platform staging, durable publication, or Artifact commit. */
public record ProviderExecutionOutput(InputStream content) implements AutoCloseable {

    public ProviderExecutionOutput {
        Objects.requireNonNull(content, "content");
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
