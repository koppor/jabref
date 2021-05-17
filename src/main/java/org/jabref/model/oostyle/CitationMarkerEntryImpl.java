package org.jabref.model.oostyle;

import java.util.Objects;
import java.util.Optional;

/**
 * Implement CitationMarkerEntry by containing the data needed.
 *
 * {@see CitationMarkerEntry} for description.
 *
 */
public class CitationMarkerEntryImpl implements CitationMarkerEntry {
    final String citationKey;
    final Optional<CitationDatabaseLookup.Result> db;
    final Optional<String> uniqueLetter;
    final Optional<OOFormattedText> pageInfo;
    final boolean isFirstAppearanceOfSource;

    public CitationMarkerEntryImpl(String citationKey,
                                   Optional<CitationDatabaseLookup.Result> databaseLookupResult,
                                   Optional<String> uniqueLetter,
                                   Optional<OOFormattedText> pageInfo,
                                   boolean isFirstAppearanceOfSource) {
        Objects.requireNonNull(citationKey);
        this.citationKey = citationKey;
        this.db = databaseLookupResult;
        this.uniqueLetter = uniqueLetter;
        this.pageInfo = pageInfo;
        this.isFirstAppearanceOfSource = isFirstAppearanceOfSource;
    }

//    public CitationMarkerEntryImpl(String citationKey,
//                                   BibEntry entry,
//                                   BibDatabase database,
//                                   String uniqueLetterQ,
//                                   String pageInfoQ,
//                                   boolean isFirstAppearanceOfSource) {
//        Optional<String> uniqueLetter = Optional.ofNullable(uniqueLetterQ);
//        Optional<OOFormattedText> pageInfo =
//            Optional.ofNullable(OOFormattedText.fromString(pageInfoQ));
//
//        Objects.requireNonNull(citationKey);
//        this.citationKey = citationKey;
//        this.db = new CitationDatabaseLookup.Result(entry, database);
//        this.uniqueLetter = uniqueLetter;
//        this.pageInfo = pageInfo;
//        this.isFirstAppearanceOfSource = isFirstAppearanceOfSource;
//    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<CitationDatabaseLookup.Result> getDatabaseLookupResult() {
        return db;
    }

    @Override
    public Optional<String> getUniqueLetter() {
        return uniqueLetter;
    }

    @Override
    public Optional<OOFormattedText> getPageInfo() {
        return pageInfo;
    }

    @Override
    public boolean getIsFirstAppearanceOfSource() {
        return isFirstAppearanceOfSource;
    }
}
