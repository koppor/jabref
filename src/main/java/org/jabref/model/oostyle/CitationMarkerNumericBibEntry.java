package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is for the numeric bibliography labels.
 */
public interface CitationMarkerNumericBibEntry {

    /**
     * For unresolved citation we show the citation key.
     */
    String getCitationKey();

    /**
     * @return Optional.empty() for unresolved
     */
    Optional<Integer> getNumber();
}
