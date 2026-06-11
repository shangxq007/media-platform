package com.example.platform.render.infrastructure.font;

import java.util.List;

public interface FontSubsetCache {

    String computeCacheKey(String fontHash, String charsHash, String optionsHash);

    boolean contains(String cacheKey);

    String getSubsetUri(String cacheKey);

    void put(String cacheKey, String subsetUri);

    void invalidate(String cacheKey);
}
