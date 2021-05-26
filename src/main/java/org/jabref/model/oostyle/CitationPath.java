package org.jabref.model.oostyle;

/**
 * Identifies a citation with the citation group containing it and
 * its storage index within.
 */
public class CitationPath {

    public final CitationGroupId group;

    public final int storageIndexInGroup;

    CitationPath(CitationGroupId group, int storageIndexInGroup) {
        this.group = group;
        this.storageIndexInGroup = storageIndexInGroup;
    }
}
