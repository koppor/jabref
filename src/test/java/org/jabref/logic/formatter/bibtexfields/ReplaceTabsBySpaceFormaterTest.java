package org.jabref.logic.formatter.bibtexfields;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceTabsBySpaceFormaterTest {

    private ReplaceTabsBySpaceFormater formatter;

    @BeforeEach
    void setUp() {
        formatter = new ReplaceTabsBySpaceFormater();
    }

    @Test
    void removeSingleTab() {
        assertEquals("single tab", formatter.format("single\ttab"));
    }

    @Test
    void removeMultipleTabs() {
        assertEquals("multiple tabs", formatter.format("multiple\t\ttabs"));
    }

    @Test
    void doNothingIfNoTab() {
        assertEquals("notab", formatter.format("notab"));
    }
}
