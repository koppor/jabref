package org.jabref.logic.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class BooktitleCheckerTest {

    private final BooktitleChecker checker = new BooktitleChecker();

    @Test
    void booktitleAcceptsIfItDoesNotEndWithConferenceOn() {
        assertEquals(
                Optional.empty(),
                checker.checkValue(
                        "2014 Fourth International Conference on Digital Information and Communication Technology and it's Applications (DICTAP)"));
    }

    @Test
    void booktitleDoesNotAcceptsIfItEndsWithConferenceOn() {
        assertNotEquals(
                Optional.empty(),
                checker.checkValue(
                        "Digital Information and Communication Technology and it's Applications (DICTAP), 2014 Fourth International Conference on"));
    }

    @Test
    void booktitleIsBlank() {
        assertEquals(Optional.empty(), checker.checkValue(" "));
    }
}
