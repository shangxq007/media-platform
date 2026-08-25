package com.example.platform.workerfabric.reuse;

import com.example.platform.workerfabric.domain.CompletionDecision;
import java.util.Objects;

/** Combined observable result of pending publication, fenced completion, and activation. */
public record FencedReuseCompletionResult(
        ReusePublicationResult publicationResult,
        CompletionDecision completionDecision) {
    public FencedReuseCompletionResult {
        Objects.requireNonNull(publicationResult, "publicationResult");
        Objects.requireNonNull(completionDecision, "completionDecision");
    }
}
