package org.jabref.logic.importer.plaincitation;

import org.jabref.logic.importer.FetcherException;
import org.jabref.model.entry.BibEntry;

import java.util.Optional;

public interface PlainCitationParser {
    Optional<BibEntry> parsePlainCitation(String text) throws FetcherException;
}
