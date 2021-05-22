package org.jabref.model.oostyle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

public class CitedKey implements CitationSort.ComparableCitation, CitationMarkerNormEntry {
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
        this.db = cit.getDatabaseLookupResult();
        this.number = cit.getNumber();
        this.uniqueLetter = cit.getUniqueLetter();
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
     *  getPageInfo always returns Optional.empty.
     *
     *  Only exists to implement CitationSort.ComparableCitation.
     *
     *  @return Optional.empty()
     */
    @Override
    public Optional<OOFormattedText> getPageInfo() {
        return Optional.empty();
    }

    /*
     * Implement CitationMarkerNormEntry
     */
    @Override
    public Optional<CitationDatabaseLookup.Result> getDatabaseLookupResult() {
        return db;
    }

    /**
     * Appends to end of {@code where}
     */
    void addPath(CitationPath p, Citation cit) {
        this.where.add(p);
        if (cit.getDatabaseLookupResult() != this.db) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.db");
        }
        if (cit.getNumber() != this.number) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.number");
        }
        if (cit.getUniqueLetter() != this.uniqueLetter) {
            throw new RuntimeException("CitedKey.addPath: mismatch on cit.uniqueLetter");
        }
    }

    void lookupInDatabases(List<BibDatabase> databases) {
        this.db = CitationDatabaseLookup.lookup(databases, this.citationKey);
    }

    void distributeDatabaseLookupResult(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setDatabaseLookupResult, db);
    }

    void distributeNumber(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setNumber, number);
    }

    void distributeUniqueLetter(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setUniqueLetter, uniqueLetter);
    }
} // class CitedKey
