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
     *  CitationEntry needs refMark or other identifying string
     */
    public String asString() {
        return id;
    }
}
