package org.jabref.logic.layout.format;

import org.jabref.logic.layout.LayoutFormatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorLastFirstTest {

    @Test
    void format() {
        LayoutFormatter a = new AuthorLastFirst();

        // Empty case
        assertEquals("", a.format(""));

        // Single Names
        assertEquals("Someone, Van Something", a.format("Van Something Someone"));

        // Two names
        assertEquals("von Neumann, John and Black Brown, Peter", a
                .format("John von Neumann and Black Brown, Peter"));

        // Three names
        assertEquals("von Neumann, John and Smith, John and Black Brown, Peter", a
                .format("von Neumann, John and Smith, John and Black Brown, Peter"));

        assertEquals("von Neumann, John and Smith, John and Black Brown, Peter", a
                .format("John von Neumann and John Smith and Black Brown, Peter"));
    }
}
