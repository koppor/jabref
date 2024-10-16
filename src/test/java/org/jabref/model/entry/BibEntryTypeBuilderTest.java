package org.jabref.model.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jabref.model.entry.field.StandardField;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

class BibEntryTypeBuilderTest {

    @Test
    @Disabled("There is just a log message written, but no exception thrown")
    void fieldAlreadySeenSameCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BibEntryTypeBuilder()
                                .withImportantFields(StandardField.AUTHOR)
                                .withImportantFields(StandardField.AUTHOR)
                                .build());
    }

    @Test
    void detailOptionalWorks() {
        BibEntryType bibEntryType =
                new BibEntryTypeBuilder()
                        .withImportantFields(StandardField.AUTHOR)
                        .withDetailFields(StandardField.NOTE)
                        .build();
        assertEquals(
                new LinkedHashSet<>(List.of(StandardField.NOTE)),
                bibEntryType.getDetailOptionalFields());
    }

    @Test
    @Disabled("There is just a log message written, but no exception thrown")
    void fieldAlreadySeenDifferentCategories() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BibEntryTypeBuilder()
                                .withRequiredFields(StandardField.AUTHOR)
                                .withImportantFields(StandardField.AUTHOR)
                                .build());
    }
}
