package org.jabref.model.oostyle;

import java.util.Objects;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

public class CitationLookupResult {

    public final BibEntry entry;
    public final BibDatabase database;

    public CitationLookupResult(BibEntry entry, BibDatabase database) {
        Objects.requireNonNull(entry);
        Objects.requireNonNull(database);
        this.entry = entry;
        this.database = database;
    }
}
