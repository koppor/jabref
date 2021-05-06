package org.jabref.logic.oostyle;

import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.OOFormattedText;

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
public interface CitationMarkerEntry {

    /** Citation key. This is what we usually get from the document.
     *
     *  Used if getBibEntry() and/or getDatabase() returns
     *  empty, which indicates failure to lookup in the databases.
     *  The marker created is "Unresolved({citationKey})".
     *
     */
    String getCitationKey();

    /** Bibliography entry looked up from databases.
     *
     * May be empty if not found. In this case getDatabase()
     * should also return empty.
     */
    Optional<BibEntry> getBibEntry();

    /**
     * The database where BibEntry was found.
     * May be empty, if not found (otherwise not).
     */
    Optional<BibDatabase> getDatabase();

    /**
     * uniqueLetter or null if not needed.
     */
    Optional<String> getUniqueLetter();

    /**
     * pageInfo for this citation, provided by the user.
     * May be empty, for none.
     */
    Optional<OOFormattedText> getPageInfo();

    /**
     *  @return true if this citation is the first appearance of the
     *  source cited. Some styles use different limit on the number of
     *  authors shown in this case.
     */
    boolean getIsFirstAppearanceOfSource();
}
