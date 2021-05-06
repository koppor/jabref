package org.jabref.logic.oostyle;

import java.util.Optional;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationDatabaseLookup;
import org.jabref.model.oostyle.OOFormattedText;

public class Citation implements CitationSort.ComparableCitation {

    /** key in database */
    public String citationKey;
    /** Result from database lookup. Optional.empty() if not found. */
    public Optional<CitationDatabaseLookup.Result> db;
    /** The number used for numbered citation styles . */
    public Optional<Integer> number;
    /** Letter that makes the in-text citation unique. */
    public Optional<String> uniqueLetter;

    /** pageInfo: For Compat.DataModel.JabRef53 */
    public Optional<OOFormattedText> pageInfo;

    /* missing: something that differentiates this from other
     * citations of the same citationKey. In particular, a
     * CitationGroup may contain multiple citations of the same
     * source. We use CitationPath.storageIndexInGroup to refer to
     * citations.
     */

    public Citation(String citationKey) {
        this.citationKey = citationKey;
        this.db = Optional.empty();
        this.number = Optional.empty();
        this.uniqueLetter = Optional.empty();
        this.pageInfo = Optional.empty();
    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<OOFormattedText> getPageInfo() {
        return pageInfo;
    }

    @Override
    public Optional<BibEntry> getBibEntry() {
        return (db.isPresent()
                ? Optional.of(db.get().entry)
                : Optional.empty());
    }
}
