package org.jabref.logic.oostyle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationDatabaseLookup;
import org.jabref.model.oostyle.OOFormattedText;

public class CitedKey implements CitationSort.ComparableCitation {
    public String citationKey;
    public LinkedHashSet<CitationPath> where;
    public Optional<CitationDatabaseLookup.Result> db;
    public Optional<Integer> number; // For Numbered citation styles.
    public Optional<String> uniqueLetter; // For AuthorYear citation styles.
    public Optional<OOFormattedText> normCitMarker;  // For AuthorYear citation styles.

    CitedKey(String citationKey, CitationPath p, Citation cit) {
        this.citationKey = citationKey;
        this.where = new LinkedHashSet<>(); // remember order
        this.where.add(p);
        this.db = cit.db;
        this.number = cit.number;
        this.uniqueLetter = cit.uniqueLetter;
        this.normCitMarker = Optional.empty();
    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<BibEntry> getBibEntry() {
        return (db.isPresent()
                ? Optional.of(db.get().entry)
                : Optional.empty());
    }

    /** No pageInfo is needed for sorting the bibliography,
     *  getPageInfo always returns Optional.empty. Only exists to implement ComparableCitation.
     *
     *  @return null
     */
    @Override
    public Optional<OOFormattedText> getPageInfo() {
        return Optional.empty();
    }

    /**
     * Appends to end of {@code where}
     */
    void addPath(CitationPath p, Citation cit) {
        this.where.add(p);
        if (cit.db != this.db) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.db");
        }
        if (cit.number != this.number) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.number");
        }
        if (cit.uniqueLetter != this.uniqueLetter) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.uniqueLetter");
        }
    }

    void lookupInDatabases(List<BibDatabase> databases) {
        this.db = CitationDatabaseLookup.lookup(databases, this.citationKey);
    }

    void distributeDatabaseLookupResult(CitationGroups cgs) {
        cgs.setDatabaseLookupResults(where, db);
    }

    void distributeNumber(CitationGroups cgs) {
        cgs.setNumbers(where, number);
    }

    void distributeUniqueLetter(CitationGroups cgs) {
        cgs.setUniqueLetters(where, uniqueLetter);
    }
} // class CitedKey
