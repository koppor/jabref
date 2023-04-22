package org.jabref.http.dto;

import com.google.gson.Gson;
import org.jabref.gui.Globals;
import org.jabref.http.server.ServerPreferences;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BibEntryDTOTest {

    private static GsonFactory gsonFactory = new GsonFactory();

    public static Stream<Arguments> checkSerialization() {
        return Stream.of(
                Arguments.of("""
                        {
                          "sharingMetadata": {
                            "sharedID": -1,
                            "version": 1
                          },
                          "userComments": "",
                          "citationKey": "",
                          "bibtex": "@Misc{,\\n}\\n"
                        }""", new BibEntry().withChanged(true)),
                Arguments.of("""
                        {
                          "sharingMetadata": {
                            "sharedID": -1,
                            "version": 1
                          },
                          "userComments": "",
                          "citationKey": "key",
                          "bibtex": "@Misc{key,\\n}\\n"
                        }""", new BibEntry().withCitationKey("key").withChanged(true)),
                Arguments.of("""
                        {
                          "sharingMetadata": {
                            "sharedID": -1,
                            "version": 1
                          },
                          "userComments": "",
                          "citationKey": "key",
                          "bibtex": "@Misc{key,\\n  author \\u003d {Author},\\n}\\n"
                        }""", new BibEntry().withCitationKey("key").withField(StandardField.AUTHOR, "Author").withChanged(true)),
                Arguments.of("""
                        {
                          "sharingMetadata": {
                            "sharedID": 1,
                            "version": 1
                          },
                          "userComments": "",
                          "citationKey": "e1.v1",
                          "bibtex": "@Misc{e1.v1,\\n}\\n"
                        }""", new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1).withChanged(true))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void checkSerialization(String expected, BibEntry entry) {
        Gson gson = gsonFactory.provide();
        assertEquals(expected, gson.toJson(new BibEntryDTO(entry)));
    }

    public static Stream<Arguments> convertToString() {
        return Stream.of(
                Arguments.of("""
                        @Misc{,
                        }
                        """, new BibEntry().withChanged(true)),
                Arguments.of("""
                        @Misc{key,
                        }
                        """, new BibEntry().withCitationKey("key").withChanged(true)),
                Arguments.of("""
                        @Misc{key,
                          author = {Author},
                        }
                        """, new BibEntry().withCitationKey("key").withField(StandardField.AUTHOR, "Author").withChanged(true)),
                Arguments.of("""
                        @Misc{e1.v1,
                        }
                        """, new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1).withChanged(true))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void convertToString(String expected, BibEntry entry) {
        assertEquals(expected, BibEntryDTO.convertToString(entry, BibDatabaseMode.BIBTEX, ServerPreferences.fieldWriterPreferences(), Globals.entryTypesManager));
    }
}
