package org.jabref.model.oostyle;

import java.util.Optional;

import org.jabref.model.entry.BibEntry;

/*
 * This is what we need to sort bibliography entries.
 */
public interface ComparableCitedKey {

    public String getCitationKey();

    public Optional<BibEntry> getBibEntry();
}

