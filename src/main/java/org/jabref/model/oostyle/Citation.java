package org.jabref.model.oostyle;

import java.util.Optional;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.openoffice.Pair;

public class Citation implements CitationSort.ComparableCitation {

    /** key in database */
    public final String citationKey;

    /** Result from database lookup. Optional.empty() if not found. */
    private Optional<CitationDatabaseLookup.Result> db;

    /** The number used for numbered citation styles . */
    private Optional<Integer> number;

    /** Letter that makes the in-text citation unique. */
    private Optional<String> uniqueLetter;

    /** pageInfo */
    private Optional<OOFormattedText> pageInfo;

    /**
     *
     */
    public Citation(String citationKey) {
        this.citationKey = citationKey;
        this.db = Optional.empty();
        this.number = Optional.empty();
        this.uniqueLetter = Optional.empty();
        this.pageInfo = Optional.empty();
    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<OOFormattedText> getPageInfo() {
        return pageInfo;
    }

    @Override
    public Optional<BibEntry> getBibEntry() {
        return (db.isPresent()
                ? Optional.of(db.get().entry)
                : Optional.empty());
    }

    public Optional<CitationDatabaseLookup.Result> getDatabaseLookupResult() {
        return db;
    }

    public Optional<Integer> getNumber() {
        return number;
    }

    public Optional<String> getUniqueLetter() {
        return uniqueLetter;
    }

    public void setPageInfo(Optional<OOFormattedText> v) {
        Optional<OOFormattedText> vv = normalizePageInfo(v);
        if (!vv.equals(v)) {
            throw new RuntimeException("setPageInfo argument is not normalied");
        }
        this.pageInfo = vv;
    }

    /*
     * Setters for CitationGroups.distribute()
     */
    public static void setDatabaseLookupResult(Pair<Citation, Optional<CitationDatabaseLookup.Result>> x) {
        Citation cit = x.a;
        cit.db = x.b;
    }

    public static void setNumber(Pair<Citation, Optional<Integer>> x) {
        Citation cit = x.a;
        cit.number = x.b;
    }

    public static void setUniqueLetter(Pair<Citation, Optional<String>> x) {
        Citation cit = x.a;
        cit.uniqueLetter = x.b;
    }

    /*
     * pageInfo normalization
     */
    public static Optional<OOFormattedText> normalizePageInfo(Optional<OOFormattedText> o) {
        if (o == null || o.isEmpty() || "".equals(OOFormattedText.toString(o.get()))) {
            return Optional.empty();
        }
        String s = OOFormattedText.toString(o.get());
        if (s.trim().equals("")) {
            return Optional.empty();
        }
        return Optional.of(OOFormattedText.fromString(s.trim()));
    }
}
