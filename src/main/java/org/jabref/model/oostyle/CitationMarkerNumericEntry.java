package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is what we need for getCitationMarker to produce numeric
 * citation markers.
 */
public interface CitationMarkerNumericEntry {

    Optional<Integer> getNumber();

    Optional<OOText> getPageInfo();
}
