package org.jabref.model.oostyle;

import java.util.Comparator;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;

/*
 * Given a Comparator<BibEntry> provide a Comparator<ComparableCitation>
 * that can handle unresolved citation keys and take pageInfo into account.
 */
public class CompareCitation implements Comparator<ComparableCitation> {

    CitedKeyComparator citedKeyComparator;

    CompareCitation(Comparator<BibEntry> entryComparator, boolean unresolvedComesFirst) {
        this.citedKeyComparator = new CitedKeyComparator(entryComparator, unresolvedComesFirst);
    }

    public int compare(ComparableCitation a, ComparableCitation b) {
        int res = citedKeyComparator.compare(a, b);

        // Also consider pageInfo
        if (res == 0) {
            res = CompareCitation.comparePageInfo(a.getPageInfo(), b.getPageInfo());
        }
        return res;
    }

    /**
     * Defines sort order for pageInfo strings.
     *
     * Optional.empty comes before non-empty.
     */
    public static int comparePageInfo(Optional<OOFormattedText> a, Optional<OOFormattedText> b) {

        Optional<OOFormattedText> aa = Citation.normalizePageInfo(a);
        Optional<OOFormattedText> bb = Citation.normalizePageInfo(b);
        if (aa.isEmpty() && bb.isEmpty()) {
            return 0;
        }
        if (aa.isEmpty()) {
            return -1;
        }
        if (bb.isEmpty()) {
            return +1;
        }
        return aa.get().asString().compareTo(bb.get().asString());
    }
}


