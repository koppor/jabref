package org.jabref.model.oostyle;

import java.util.Objects;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

/**
 * Implement CitationMarkerEntry by containing the data needed.
 *
 * {@see CitationMarkerEntry} for description.
 *
 */
public class CitationMarkerEntryImpl implements CitationMarkerEntry {
    final String citationKey;
    final Optional<BibEntry> bibEntry;
    final Optional<BibDatabase> database;
    final Optional<String> uniqueLetter;
    final Optional<OOFormattedText> pageInfo;
    final boolean isFirstAppearanceOfSource;

    public CitationMarkerEntryImpl(String citationKey,
                                   Optional<BibEntry> bibEntry,
                                   Optional<BibDatabase> database,
                                   Optional<String> uniqueLetter,
                                   Optional<OOFormattedText> pageInfo,
                                   boolean isFirstAppearanceOfSource) {
        Objects.requireNonNull(citationKey);
        this.citationKey = citationKey;

        if (bibEntry.isEmpty() && database.isPresent()) {
            throw new RuntimeException("CitationMarkerEntryImpl:"
                                       + " bibEntry is present, but database is not");
        }

        if (bibEntry.isPresent() && database.isEmpty()) {
            throw new RuntimeException("CitationMarkerEntryImpl:"
                                       + " bibEntry missing, but database is present");
        }

        this.bibEntry = bibEntry;
        this.database = database;
        this.uniqueLetter = uniqueLetter;
        this.pageInfo = pageInfo;
        this.isFirstAppearanceOfSource = isFirstAppearanceOfSource;
    }

    public CitationMarkerEntryImpl(String citationKey,
                                   BibEntry bibEntryQ,
                                   BibDatabase databaseQ,
                                   String uniqueLetterQ,
                                   String pageInfoQ,
                                   boolean isFirstAppearanceOfSource) {
        Objects.requireNonNull(citationKey);
        this.citationKey = citationKey;
        Optional<BibEntry> bibEntry = Optional.ofNullable(bibEntryQ);
        Optional<BibDatabase> database = Optional.ofNullable(databaseQ);
        Optional<String> uniqueLetter = Optional.ofNullable(uniqueLetterQ);
        Optional<OOFormattedText> pageInfo =
            Optional.ofNullable(OOFormattedText.fromString(pageInfoQ));

        if (bibEntry.isEmpty() && database.isPresent()) {
            throw new RuntimeException("CitationMarkerEntryImpl:"
                                       + " bibEntry is present, but database is not");
        }
        if (bibEntry.isPresent() && database.isEmpty()) {
            throw new RuntimeException("CitationMarkerEntryImpl:"
                                       + " bibEntry missing, but database is present");
        }

        this.bibEntry = bibEntry;
        this.database = database;
        this.uniqueLetter = uniqueLetter;
        this.pageInfo = pageInfo;
        this.isFirstAppearanceOfSource = isFirstAppearanceOfSource;
    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<BibEntry> getBibEntry() {
        return bibEntry;
    }

    @Override
    public Optional<BibDatabase> getDatabase() {
        return database;
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
