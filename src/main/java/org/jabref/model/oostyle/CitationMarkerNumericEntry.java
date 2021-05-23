package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is what we need for getCitationMarker to produce numeric
 * citation markers.
 */
public interface CitationMarkerNumericEntry {

    String getCitationKey();

    Optional<Integer> getNumber();

    Optional<OOText> getPageInfo();
}
