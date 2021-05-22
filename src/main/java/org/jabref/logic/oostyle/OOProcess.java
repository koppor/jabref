package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jabref.logic.bibtex.comparator.FieldComparator;
import org.jabref.logic.bibtex.comparator.FieldComparatorStack;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.CitedKey;
import org.jabref.model.oostyle.CitedKeys;
import org.jabref.model.oostyle.OOFormattedText;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OOProcess {

    static final Comparator<BibEntry> AUTHOR_YEAR_TITLE_COMPARATOR = makeAuthorYearTitleComparator();
    static final Comparator<BibEntry> YEAR_AUTHOR_TITLE_COMPARATOR = makeYearAuthorTitleComparator();

    private static final Logger LOGGER = LoggerFactory.getLogger(OOProcess.class);

    private static Comparator<BibEntry> makeAuthorYearTitleComparator() {
        FieldComparator a = new FieldComparator(StandardField.AUTHOR);
        FieldComparator y = new FieldComparator(StandardField.YEAR);
        FieldComparator t = new FieldComparator(StandardField.TITLE);

        List<Comparator<BibEntry>> ayt = new ArrayList<>(3);
        ayt.add(a);
        ayt.add(y);
        ayt.add(t);
        return new FieldComparatorStack<>(ayt);
    }

    private static Comparator<BibEntry> makeYearAuthorTitleComparator() {
        FieldComparator y = new FieldComparator(StandardField.YEAR);
        FieldComparator a = new FieldComparator(StandardField.AUTHOR);
        FieldComparator t = new FieldComparator(StandardField.TITLE);

        List<Comparator<BibEntry>> yat = new ArrayList<>(3);
        yat.add(y);
        yat.add(a);
        yat.add(t);
        return new FieldComparatorStack<>(yat);
    }

    /**
     *  The comparator used to sort within a group of merged
     *  citations.
     *
     *  The term used here is "multicite". The option controlling the
     *  order is "MultiCiteChronological" in style files.
     *
     *  Yes, they are always sorted one way or another.
     */
    public static Comparator<BibEntry> comparatorForMulticite(OOBibStyle style) {
        if (style.getMultiCiteChronological()) {
            return OOProcess.YEAR_AUTHOR_TITLE_COMPARATOR;
        } else {
            return OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR;
        }
    }

    /**
     * The main field is citMarkers, the rest is for reuse in caller.
     */
    public static class ProduceCitationMarkersResult {

        public CitationGroups cgs;

        /** citation markers */
        public Map<CitationGroupID, OOFormattedText> citMarkers;

        ProduceCitationMarkersResult(CitationGroups cgs,
                                     Map<CitationGroupID, OOFormattedText> citMarkers) {
            this.cgs = cgs;
            this.citMarkers = citMarkers;
            if (cgs.getBibliography().isEmpty()) {
                throw new RuntimeException("ProduceCitationMarkersResult.constructor:"
                                           + " cgs does not have a bibliography");
            }
        }

        public CitedKeys getBibliography() {
            if (cgs.getBibliography().isEmpty()) {
                throw new RuntimeException("ProduceCitationMarkersResult.getBibliography:"
                                           + " cgs does not have a bibliography");
            }
            return cgs.getBibliography().get();
        }

        public List<String> getUnresolvedKeys() {
            CitedKeys bib = getBibliography();
            List<String> unresolvedKeys = new ArrayList<>();
            for (CitedKey ck : bib.values()) {
                if (ck.db.isEmpty()) {
                    unresolvedKeys.add(ck.citationKey);
                }
            }
            return unresolvedKeys;
        }
    }

    public static ProduceCitationMarkersResult produceCitationMarkers(CitationGroups cgs,
                                                                      List<BibDatabase> databases,
                                                                      OOBibStyle style) {

        if (!cgs.hasGlobalOrder()) {
            throw new RuntimeException("produceCitationMarkers: globalOrder is misssing in cgs");
        }

        cgs.lookupEntriesInDatabases(databases);

        // requires cgs.lookupEntryInDatabases: needs BibEntry data
        cgs.imposeLocalOrderByComparator(comparatorForMulticite(style));

        Map<CitationGroupID, OOFormattedText> citMarkers;

        // fill citMarkers
        Map<String, String> uniqueLetters = new HashMap<>();

        if (style.isCitationKeyCiteMarkers()) {
            citMarkers = OOProcessCitationKeyMarkers.produceCitationMarkers(cgs, style);
        } else if (style.isNumberEntries()) {
            citMarkers = OOProcessNumericMarkers.produceCitationMarkers(cgs, style);
        } else {
            /* Normal case, (!isCitationKeyCiteMarkers && !isNumberEntries) */
            citMarkers = OOProcessAuthorYearMarkers.produceCitationMarkers(cgs, style);
        }

        return new ProduceCitationMarkersResult(cgs, /* has bibliography as a side effect */
                                                citMarkers);
    }

}
