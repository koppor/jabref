package org.jabref.http.dto;

import java.io.ByteArrayInputStream;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BibEntryJsonReaderTest {
    private BibEntry initialEntry = new BibEntry()
            .withCitationKey("key")
            .withField(StandardField.AUTHOR, "author");

    @Test
    void test() throws Exception {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("""
                {
                  "sharingMetadata": {
                    "sharedID": -1,
                    "version": 1
                  },
                  "type": "Misc",
                  "citationKey": "key",
                  "content": {
                    "AUTHOR": "author"
                  },
                  "userComments": ""
                }""".getBytes());
        BibEntry result = new BibEntryJsonReader().readFrom(BibEntry.class,
                null,
                null,
                null,
                null,
                byteArrayInputStream
        );
        assertEquals(initialEntry, result);
    }

}
