package org.jabref.logic.layout.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jabref.logic.layout.LayoutFormatter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class FirstPageTest {

    private LayoutFormatter firstPageLayoutFormatter = new FirstPage();

    @ParameterizedTest
    @MethodSource("providePages")
    void formatPages(String formattedFirstPage, String originalPages) {
        assertEquals(formattedFirstPage, firstPageLayoutFormatter.format(originalPages));
    }

    private static Stream<Arguments> providePages() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("", null),
                Arguments.of("345", "345"),
                Arguments.of("345", "345-350"),
                Arguments.of("345", "345--350"));
    }
}
