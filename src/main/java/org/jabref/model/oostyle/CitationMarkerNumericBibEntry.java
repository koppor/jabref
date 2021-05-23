package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is for the numeric bibliography labels.
 *
 * getNumber() returning Optional.empty() indicates unresolved
 * citation. In this case we also show the citation key to help
 * finding the problematic citations.
 */
public interface CitationMarkerNumericBibEntry {

    String getCitationKey();

    Optional<Integer> getNumber();
}
