package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is what we need for getCitationMarker to produce author-year
 * citation markers.
 *
 * Citation misses two things
 *   - isFirstAppearanceOfSource : could be extended to provide this.
 *   - pageInfo under DataModel JabRef52 needs CitationGroup
 *
 * CitedKey is used for creating normalizedCitationMarker, so we do
 * not need pageInfo, uniqueLetter and isFirstAppearanceOfSource.
 *
 */
public interface CitationMarkerEntry extends CitationMarkerNormEntry {

    /**
     * uniqueLetter or Optional.empty() if not needed.
     */
    Optional<String> getUniqueLetter();

    /**
     * pageInfo for this citation, provided by the user.
     * May be empty, for none.
     */
    Optional<OOText> getPageInfo();

    /**
     *  @return true if this citation is the first appearance of the
     *  source cited. Some styles use different limit on the number of
     *  authors shown in this case.
     */
    boolean getIsFirstAppearanceOfSource();
}
