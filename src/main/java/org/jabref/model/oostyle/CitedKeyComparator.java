package org.jabref.model.oostyle;

import java.util.Comparator;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;

/*
 * Given a Comparator<BibEntry> provide a Comparator<ComparableCitedKey>
 * that also handles unresolved citation keys.
 */
public class CitedKeyComparator implements Comparator<ComparableCitedKey> {

    Comparator<BibEntry> entryComparator;
    boolean unresolvedComesFirst;

    CitedKeyComparator(Comparator<BibEntry> entryComparator, boolean unresolvedComesFirst) {
        this.entryComparator = entryComparator;
        this.unresolvedComesFirst = unresolvedComesFirst;
    }

    public int compare(ComparableCitedKey a, ComparableCitedKey b) {
        Optional<BibEntry> aBibEntry = a.getBibEntry();
        Optional<BibEntry> bBibEntry = b.getBibEntry();
        final int mul = unresolvedComesFirst ? (+1) : (-1);

        int res = 0;
        if (aBibEntry.isEmpty() && bBibEntry.isEmpty()) {
            // Both are unresolved: compare them by citation key.
            res = a.getCitationKey().compareTo(b.getCitationKey());
        } else if (aBibEntry.isEmpty()) {
            return -mul;
        } else if (bBibEntry.isEmpty()) {
            return mul;
        } else {
            // Proper comparison of entries
            res = entryComparator.compare(aBibEntry.get(), bBibEntry.get());
        }
        return res;
    }
}
