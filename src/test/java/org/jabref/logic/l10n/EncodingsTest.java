package org.jabref.logic.l10n;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EncodingsTest {
    @Test
    void charsetsShouldNotBeNull() {
        assertNotNull(Encodings.ENCODINGS);
    }

    @Test
    void displayNamesShouldNotBeNull() {
        assertNotNull(Encodings.ENCODINGS_DISPLAYNAMES);
    }

    @Test
    void charsetsShouldNotBeEmpty() {
        assertNotEquals(0, Encodings.ENCODINGS.length);
    }

    @Test
    void displayNamesShouldNotBeEmpty() {
        assertNotEquals(0, Encodings.ENCODINGS_DISPLAYNAMES.length);
    }
}
