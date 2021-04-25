package org.jabref.logic.oostyle;

/**
 * Identifies a citation with the citation group containing it and
 * its storage index within.
 */
public class CitationPath {
    public final CitationGroupID group;
    public final int storageIndexInGroup;
    CitationPath(CitationGroupID group,
                 int storageIndexInGroup) {
        this.group = group;
        this.storageIndexInGroup = storageIndexInGroup;
    }
}
