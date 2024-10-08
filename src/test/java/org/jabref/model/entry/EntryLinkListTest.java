package org.jabref.model.entry;

import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryLinkListTest {

    private static final String KEY = "test";

    private BibDatabase database;
    private List<ParsedEntryLink> links;
    private ParsedEntryLink link;
    private BibEntry source;
    private BibEntry target;

    @BeforeEach
    void before() {
        database = new BibDatabase();
        links = EntryLinkList.parse(KEY, database);
        link = links.getFirst();
        source = create("source");
        target = create("target");
    }

    private BibEntry create(String citeKey) {
        BibEntry entry = new BibEntry()
                .withCitationKey(citeKey);
        database.insertEntry(entry);
        return entry;
    }

    @Test
    void givenFieldValueAndDatabaseWhenParsingThenExpectKey() {
        assertEquals(KEY, link.getKey());
    }

    @Test
    void givenFieldValueAndDatabaseWhenParsingThenExpectDataBase() {
        assertEquals(database, link.getDatabase());
    }

    @Test
    void givenFieldValueAndDatabaseWhenParsingThenExpectEmptyLinkedEntry() {
        assertEquals(Optional.empty(), link.getLinkedEntry());
    }

    @Test
    void givenFieldValueAndDatabaseWhenParsingThenExpectLink() {
        ParsedEntryLink expected = new ParsedEntryLink(KEY, database);
        assertEquals(expected, link);
    }

    @Test
    void givenBibEntryWhenParsingThenExpectLink() {
      ParsedEntryLink expected = new ParsedEntryLink(new BibEntry().withCitationKey("key"));
      assertFalse(expected.getLinkedEntry().isEmpty());
    }

    @Test
    void givenNullFieldValueAndDatabaseWhenParsingThenExpectLinksIsEmpty() {
        links = EntryLinkList.parse(null, database);
        assertTrue(links.isEmpty());
    }

    @Test
    void givenTargetAndSourceWhenSourceCrossrefTargetThenSourceCrossrefsTarget() {
        source.setField(StandardField.CROSSREF, "target");
        assertSourceCrossrefsTarget(target, source);
    }

    private void assertSourceCrossrefsTarget(BibEntry target, BibEntry source) {
        Optional<String> sourceCrossref = source.getField(StandardField.CROSSREF);
        Optional<String> targetCiteKey = target.getCitationKey();
        assertEquals(sourceCrossref, targetCiteKey);
    }
}
