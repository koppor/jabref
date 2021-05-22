package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is what we need to produce normalized author-year citation
 * markers.
 */
public interface CitationMarkerNormEntry {

    /** Citation key. This is what we usually get from the document.
     *
     *  Used if getBibEntry() and/or getDatabase() returns
     *  empty, which indicates failure to lookup in the databases.
     *  The marker created is "Unresolved({citationKey})".
     *
     */
    String getCitationKey();

    /** Result of looking up citation key in databases.
     */
    Optional<CitationDatabaseLookupResult> getDatabaseLookupResult();
}
