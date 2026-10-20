package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordCursor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordCursorCodecTest {

    /** Characters that aren't URL-safe and the time's full precision must survive the round trip. */
    private static final RecordCursor CURSOR =
            new RecordCursor("dst/ä 1", OffsetDateTime.parse("2026-10-20T10:00:00.123456Z"), "r1");

    private final RecordCursorCodec codec = new RecordCursorCodec(JsonMapper.builder().build());

    private static String tokenOf(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encodesToUrlSafeTokenThatDecodesToTheSameCursor() {
        String token = codec.encode(CURSOR);

        assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
        assertEquals(CURSOR, codec.decode(token));
    }

    @Test
    void rejectsTokensItDidNotProduce() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not base64!"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tokenOf("{not json")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tokenOf("42")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tokenOf("""
                {"destinationId": "dst-1", "recordId": "r1"}""")));   // no time
    }
}
