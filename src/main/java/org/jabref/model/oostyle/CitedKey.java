package org.jabref.model.oostyle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

public class CitedKey implements ComparableCitedKey, CitationMarkerNormEntry {
    public String citationKey;
    public LinkedHashSet<CitationPath> where;
    public Optional<CitationLookupResult> db;
    public Optional<Integer> number; // For Numbered citation styles.
    public Optional<String> uniqueLetter; // For AuthorYear citation styles.
    public Optional<OOFormattedText> normCitMarker;  // For AuthorYear citation styles.

    CitedKey(String citationKey, CitationPath p, Citation cit) {
        this.citationKey = citationKey;
        this.where = new LinkedHashSet<>(); // remember order
        this.where.add(p);
        this.db = cit.getLookupResult();
        this.number = cit.getNumber();
        this.uniqueLetter = cit.getUniqueLetter();
        this.normCitMarker = Optional.empty();
    }

    /*
     * Implement ComparableCitedKey
     */
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

    /*
     * Implement CitationMarkerNormEntry
     */
    @Override
    public Optional<CitationLookupResult> getLookupResult() {
        return db;
    }

    /**
     * Appends to end of {@code where}
     */
    void addPath(CitationPath p, Citation cit) {
        this.where.add(p);
        if (cit.getLookupResult() != this.db) {
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
        this.db = Citation.lookup(databases, this.citationKey);
    }

    void distributeLookupResult(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setLookupResult, db);
    }

    void distributeNumber(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setNumber, number);
    }

    void distributeUniqueLetter(CitationGroups cgs) {
        cgs.distributeToCitations(where, Citation::setUniqueLetter, uniqueLetter);
    }
} // class CitedKey
