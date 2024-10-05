package org.jabref.model.entry.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class MathSciNetIdTest {

    @Test
    void parseRemovesNewLineCharacterAtEnd() throws Exception {
        Optional<MathSciNetId> id = MathSciNetId.parse("3014184\n");
        assertEquals(Optional.of(new MathSciNetId("3014184")), id);
    }
}
