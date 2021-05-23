package org.jabref.model.oostyle;

import java.util.Optional;

/*
 * Minimal implementation for CitationMarkerNumericEntry
 */
public class CitationMarkerNumericEntryImpl implements CitationMarkerNumericEntry {

    /*
     * The number encoding "this entry is unresolved" for the constructor.
     */
    public final static int UNRESOLVED_ENTRY_NUMBER = 0;

    private Optional<Integer> num;
    private Optional<OOText> pageInfo;

    public CitationMarkerNumericEntryImpl(int num, Optional<OOText> pageInfo) {
        this.num = (num == UNRESOLVED_ENTRY_NUMBER
                    ? Optional.empty()
                    : Optional.of(num));
        this.pageInfo = pageInfo;
    }

    @Override
    public Optional<Integer> getNumber() {
        return num;
    }

    @Override
    public Optional<OOText> getPageInfo() {
        return pageInfo;
    }
}
