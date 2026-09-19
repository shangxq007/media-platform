package com.example.platform.operation.invocation;

import com.example.platform.operation.operation.OperationRequest;

/**
 * Operation-owned application boundary for executing an already typed request.
 */
@org.springframework.modulith.NamedInterface("invocation")
public interface OperationInvocationPort {

    /** Side-effect-free validation through the same canonical owner; no second operation registry. */
    void validate(OperationRequest request, OperationInvocationContext context, String projectId);

    OperationInvocationResult invoke(OperationRequest request, OperationInvocationContext context);
}
