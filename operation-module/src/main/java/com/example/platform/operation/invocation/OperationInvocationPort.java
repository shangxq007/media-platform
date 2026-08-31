package com.example.platform.operation.invocation;

import com.example.platform.operation.operation.OperationRequest;

/**
 * Operation-owned application boundary for executing an already typed request.
 */
@org.springframework.modulith.NamedInterface("invocation")
public interface OperationInvocationPort {

    OperationInvocationResult invoke(OperationRequest request, OperationInvocationContext context);
}
