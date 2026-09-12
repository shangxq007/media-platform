package com.example.platform.social.api.dto;

import java.util.List;

/** A bounded window only; this response never claims a complete history. */
public record PublicationPostListResponse(
        List<PublicationPostResponse> items,
        Coverage coverage) {

    public PublicationPostListResponse {
        items = List.copyOf(items);
    }

    public enum Coverage { BOUNDED_PARTIAL }
}
