package com.example.platform.timeline.canonical;

import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Exact rational MediaTime JSON codec: {@code num/den} string form
 * (CANONICAL_TIMELINE_SERIALIZATION_V2 — exact, never double).
 * Round-trips {@code MediaTime.toString()} losslessly.
 */
public final class MediaTimeJsonCodec {

    private MediaTimeJsonCodec() {}

    public static final class Serializer extends JsonSerializer<MediaTime> {
        @Override
        public void serialize(MediaTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.toString());
        }
    }

    public static final class Deserializer extends JsonDeserializer<MediaTime> {
        @Override
        public MediaTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                throw new IOException("MediaTime must not be blank");
            }
            if ("0".equals(text)) {
                return MediaTime.ZERO;
            }
            int slash = text.indexOf('/');
            if (slash < 1 || slash == text.length() - 1) {
                throw new IOException("Invalid exact MediaTime: " + text);
            }
            try {
                long num = Long.parseLong(text.substring(0, slash).trim());
                long den = Long.parseLong(text.substring(slash + 1).trim());
                if (den <= 0) {
                    throw new IOException("MediaTime denominator must be > 0: " + text);
                }
                return MediaTime.ofRational(num, den);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid exact MediaTime: " + text, e);
            }
        }
    }
}
