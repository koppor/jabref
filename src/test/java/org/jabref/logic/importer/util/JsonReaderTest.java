package org.jabref.logic.importer.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.jabref.logic.importer.ParseException;

import kong.unirest.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonReaderTest {

    @Test
    void nullStreamThrowsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            JsonReader.toJsonObject(null);
        });
    }

    @Test
    void invalidJsonThrowsParserException() {
        Assertions.assertThrows(ParseException.class, () -> {
            JsonReader.toJsonObject(new ByteArrayInputStream("invalid JSON".getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void emptyStringResultsInEmptyObject() throws Exception {
        JSONObject result = JsonReader.toJsonObject(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        assertEquals("{}", result.toString());
    }

    @Test
    void arrayThrowsParserException() {
        // Reason: We expect a JSON object, not a JSON array
        Assertions.assertThrows(ParseException.class, () -> {
            JsonReader.toJsonObject(new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void exampleJsonResultsInSameJson() throws Exception {
        String input = "{\"name\":\"test\"}";
        JSONObject result = JsonReader.toJsonObject(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        assertEquals(input, result.toString());
    }
}
