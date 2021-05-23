package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is for the bibliography labels.
 */
public interface CitationMarkerNumericBibEntry {

    String getCitationKey();

    Optional<Integer> getNumber();
}
