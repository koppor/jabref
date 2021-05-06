package org.jabref.logic.oostyle;

import java.util.Comparator;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.OOFormattedText;

class CitationSort {

    interface ComparableCitation {

        public String getCitationKey();

        public Optional<BibEntry> getBibEntry();

        public Optional<OOFormattedText> getPageInfo();
    }

    static class CitationComparator implements Comparator<ComparableCitation> {

        Comparator<BibEntry> entryComparator;
        boolean unresolvedComesFirst;

        CitationComparator(Comparator<BibEntry> entryComparator,
                           boolean unresolvedComesFirst) {
            this.entryComparator = entryComparator;
            this.unresolvedComesFirst = unresolvedComesFirst;
        }

        public int compare(ComparableCitation a, ComparableCitation b) {
            Optional<BibEntry> abe = a.getBibEntry();
            Optional<BibEntry> bbe = b.getBibEntry();
            final int mul = unresolvedComesFirst ? (+1) : (-1);

            int res = 0;
            if (abe.isEmpty() && bbe.isEmpty()) {
                // Both are unresolved: compare them by citation key.
                String ack = a.getCitationKey();
                String bck = b.getCitationKey();
                res = ack.compareTo(bck);
            } else if (abe.isEmpty()) {
                return -mul;
            } else if (bbe.isEmpty()) {
                return mul;
            } else {
                // Proper comparison of entries
                res = entryComparator.compare(abe.get(),
                                              bbe.get());
            }
            // Also consider pageInfo
            if (res == 0) {
                OOBibStyle.comparePageInfo(a.getPageInfo().orElse(null),
                                           b.getPageInfo().orElse(null));
            }
            return res;
        }
    }

}
