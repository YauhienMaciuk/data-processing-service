package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordCursor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

/**
 * Converts a {@link RecordCursor} to the opaque, URL-safe token clients pass back in the {@code cursor} query
 * parameter, and back. Tokens are Base64-encoded JSON, but clients must not depend on that.
 */
@Component
public class RecordCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final JsonMapper jsonMapper;

    public RecordCursorCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(RecordCursor cursor) {
        return ENCODER.encodeToString(jsonMapper.writeValueAsBytes(cursor));
    }

    /**
     * @throws IllegalArgumentException if the token is not one produced by {@link #encode}
     */
    public RecordCursor decode(String token) {
        try {
            return jsonMapper.readValue(DECODER.decode(token), RecordCursor.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid cursor token", e);
        }
    }
}
