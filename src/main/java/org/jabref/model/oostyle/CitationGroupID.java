package org.jabref.model.oostyle;

/**
 * Identifies a citation group in a document.
 */
public class CitationGroupID {
    String id;
    public CitationGroupID(String id) {
        this.id = id;
    }

    /**
     * CitationEntry needs some string identifying the group
     * that it can pass back later.
     */
    public String citationGroupIdAsString() {
        return id;
    }
}
