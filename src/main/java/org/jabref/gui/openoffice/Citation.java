package org.jabref.gui.openoffice;

import java.util.Optional;

import org.jabref.model.entry.BibEntry;

class Citation implements CitationSort.ComparableCitation {

    /** key in database */
    String citationKey;
    /** Result from database lookup. Optional.empty() if not found. */
    Optional<CitationDatabaseLookup.Result> db;
    /** The number used for numbered citation styles . */
    Optional<Integer> number;
    /** Letter that makes the in-text citation unique. */
    Optional<String> uniqueLetter;

    /** pageInfo: For Compat.DataModel.JabRef53 */
    Optional<String> pageInfo;

    /* missing: something that differentiates this from other
     * citations of the same citationKey. In particular, a
     * CitationGroup may contain multiple citations of the same
     * source. We use CitationPath.storageIndexInGroup to refer to
     * citations.
     */

    // TODO: Citation constructor needs dataModel, to check
    //       if usage of pageInfo confirms to expectations.
    Citation(String citationKey) {
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
    public Optional<String> getPageInfo() {
        return pageInfo;
    }

    @Override
    public Optional<BibEntry> getBibEntry() {
        return (db.isPresent()
                ? Optional.of(db.get().entry)
                : Optional.empty());
    }
}
