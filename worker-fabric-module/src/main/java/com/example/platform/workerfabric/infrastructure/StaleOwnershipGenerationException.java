package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;

/** Database-authoritative rejection of a mutation issued under a non-current generation. */
public final class StaleOwnershipGenerationException extends RuntimeException {

    public StaleOwnershipGenerationException(
            ExecutionOwnershipGeneration supplied,
            ExecutionOwnershipGeneration current) {
        super("stale execution ownership generation " + supplied.value()
                + "; current generation is " + current.value());
    }
}
