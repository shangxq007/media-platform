package com.example.platform.render.domain.asset.search;

/**
 * A field that matched the search query, with its score contribution.
 */
public record MatchedField(
        String field,
        String value,
        int scoreContribution) {

    public static MatchedField of(String field, String value, int score) {
        return new MatchedField(field, value, score);
    }
}
