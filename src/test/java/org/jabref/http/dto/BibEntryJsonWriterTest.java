package org.jabref.http.dto;

import java.io.ByteArrayOutputStream;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BibEntryJsonWriterTest {

    private BibEntry initialEntry = new BibEntry()
            .withCitationKey("key")
            .withField(StandardField.AUTHOR, "author");

    @Test
    void test() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new BibEntryJsonWriter().writeTo(initialEntry,
                null,
                null,
                null,
                null,
                null,
                byteArrayOutputStream
        );
        assertEquals("""
                {
                  "sharingMetadata": {
                    "sharedID": -1,
                    "version": 1
                  },
                  "type": "Misc",
                  "citationKey": "key",
                  "content": {
                    "author": "author"
                  },
                  "userComments": ""
                }""", byteArrayOutputStream.toString());
    }
}
