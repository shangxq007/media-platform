package com.example.platform.marketplace.internal;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class MarketplaceJson {
    private static final ObjectMapper JSON=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    static String write(Object value) { try {return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid Marketplace value",e);} }
    static <T> T read(String value,Class<T> type) {try{return JSON.readValue(value,type);}catch(Exception e){throw new IllegalArgumentException("Invalid Marketplace value",e);} }
    static String digest(Object value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalArgumentException(e);} }
}
