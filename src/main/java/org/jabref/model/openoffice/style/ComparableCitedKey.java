package org.jabref.model.openoffice.style;

import org.jabref.model.entry.BibEntry;

import java.util.Optional;

/**
 * This is what we need to sort bibliography entries.
 */
public interface ComparableCitedKey {

    String getCitationKey();

    Optional<BibEntry> getBibEntry();
}
