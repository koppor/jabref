package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.logic.bibtex.comparator.FieldComparator;
import org.jabref.logic.bibtex.comparator.FieldComparatorStack;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OOProcess {
    /* Types of in-text citation. (itcType)
     * Their numeric values are used in reference mark names.
     */
    public static final int AUTHORYEAR_PAR = 1;
    public static final int AUTHORYEAR_INTEXT = 2;
    public static final int INVISIBLE_CIT = 3;

    private static final Comparator<BibEntry> AUTHOR_YEAR_TITLE_COMPARATOR =
        makeAuthorYearTitleComparator();
    private static final Comparator<BibEntry> YEAR_AUTHOR_TITLE_COMPARATOR =
        makeYearAuthorTitleComparator();

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
    private static Comparator<BibEntry> comparatorForMulticite(OOBibStyle style) {
        if (style.getMultiCiteChronological()) {
            return OOProcess.YEAR_AUTHOR_TITLE_COMPARATOR;
        } else {
            return OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR;
        }
    }

    /* ***************************************
     *
     *     Make them unique: uniqueLetters or numbers
     *
     * **************************************/

    private static String normalizedCitationMarkerForNormalStyle(CitedKey ck,
                                                                 OOBibStyle style) {
        if (ck.db.isEmpty()) {
            return String.format("(Unresolved(%s))", ck.citationKey);
        }
        // We need "normalized" (in parenthesis) markers
        // for uniqueness checking purposes:
        // createNormalizedCitationMarker
        CitationMarkerEntry ce = new CitationMarkerEntryImpl(ck.citationKey,
                                                             ck.db.map(e -> e.entry),
                                                             ck.db.map(e -> e.database),
                                                             Optional.empty(), // uniqueLetter
                                                             Optional.empty(), // pageInfo
                                                             false /* isFirstAppearanceOfSource */);
        return style.getNormalizedCitationMarker(ce);
    }

    /**
     *  Fills {@code sortedCitedKeys//normCitMarker}
     */
    private static void createNormalizedCitationMarkersForNormalStyle(CitedKeys sortedCitedKeys,
                                                                      OOBibStyle style) {

        for (CitedKey ck : sortedCitedKeys.data.values()) {
            ck.normCitMarker = Optional.of(normalizedCitationMarkerForNormalStyle(ck, style));
        }
    }

    /**
     *  For each cited source make the citation keys unique by setting
     *  the uniqueLetter fields to letters ("a", "b") or Optional.empty()
     *
     * precondition: sortedCitedKeys already has normalized citation markers.
     * precondition: sortedCitedKeys is sorted (according to the order we want the letters to be assigned)
     *
     * Expects to see data for all cited sources here.
     * Clears uniqueLetters before filling.
     *
     * On return: Each citedKey in sortedCitedKeys has uniqueLetter set as needed.
     *            The same values are copied to the corresponding citations in cgs.
     *
     *  Depends on: style, citations and their order.
     */
    private static void createUniqueLetters(CitedKeys sortedCitedKeys,
                                            CitationGroups cgs) {

        // ncm2clks: ncm (normCitMarker) to clks (clashing keys : list of citation keys fighting for it).
        //
        //          The entries in the clks lists preserve firstAppearance order
        //          from sortedCitedKeys.data.values().
        //
        //          The index of the citationKey in this order will decide which
        //          unique letter it receives.
        //
        Map<String, List<String>> ncm2clks = new HashMap<>();
        for (CitedKey ck : sortedCitedKeys.values()) {
            String ncm = ck.normCitMarker.get();
            String citationKey = ck.citationKey;

            if (!ncm2clks.containsKey(ncm)) {
                // Found new normCitMarker
                List<String> clks = new ArrayList<>(1);
                ncm2clks.put(ncm, clks);
                clks.add(citationKey);
            } else {
                List<String> clks = ncm2clks.get(ncm);
                if (!clks.contains(citationKey)) {
                    // First appearance of citationKey, add to list.
                    clks.add(citationKey);
                }
            }
        }

        // Clear old uniqueLetter values.
        for (CitedKey ck : sortedCitedKeys.data.values()) {
            ck.uniqueLetter = Optional.empty();
        }

        // For sets of citation keys figthing for a normCitMarker
        // add unique letters to the year.
        for (List<String> clks : ncm2clks.values()) {
            if (clks.size() <= 1) {
                continue; // No fight, no letters.
            }
            // Multiple citation keys: they get their letters according to their order in clks.
            int nextUniqueLetter = 'a';
            for (String citationKey : clks) {
                // uniqueLetters.put(citationKey, String.valueOf((char) nextUniqueLetter));
                String ul = String.valueOf((char) nextUniqueLetter);
                sortedCitedKeys.data.get(citationKey).uniqueLetter = Optional.of(ul);
                nextUniqueLetter++;
            }
        }
        sortedCitedKeys.distributeUniqueLetters(cgs);
    }

    /* ***************************************
     *
     *     Calculate presentation of citation groups
     *     (create citMarkers)
     *
     * **************************************/

    /**
     * Given the withText and inParenthesis options,
     * return the corresponding itcType.
     *
     * @param withText False means invisible citation (no text).
     * @param inParenthesis True means "(Au and Thor 2000)".
     *                      False means "Au and Thor (2000)".
     */
    public static int citationTypeFromOptions(boolean withText, boolean inParenthesis) {
        if (!withText) {
            return OOProcess.INVISIBLE_CIT;
        }
        return (inParenthesis
                ? OOProcess.AUTHORYEAR_PAR
                : OOProcess.AUTHORYEAR_INTEXT);
    }

    /**
     *  Produce citation markers for the case when the citation
     *  markers are the citation keys themselves, separated by commas.
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForIsCitationKeyCiteMarkers(CitationGroups cgs,
                                                      OOBibStyle style) {

        assert style.isCitationKeyCiteMarkers();

        cgs.createPlainBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            List<Citation> cits = cgs.getSortedCitations(cgid);
            String citMarker =
                style.getCitationGroupMarkupBefore()
                + (cits.stream()
                   .map(cit -> cit.citationKey)
                   .collect(Collectors.joining(",")))
                + style.getCitationGroupMarkupAfter();
            citMarkers.put(cgid, citMarker);
        }
        return citMarkers;
    }

    /**
     * Produce citation markers for the case of numbered citations
     * with bibliography sorted by first appearance in the text.
     *
     * @param cgs
     * @param style
     *
     * @return Numbered citation markers for each CitationGroupID.
     *         Numbering is according to first appearance.
     *         Assumes global order and local order ae already applied.
     *
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForIsNumberEntriesIsSortByPosition(CitationGroups cgs,
                                                             OOBibStyle style) {

        assert style.isNumberEntries();
        assert style.isSortByPosition();

        cgs.createNumberedBibliographySortedInOrderOfAppearance();

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Integer> numbers = cg.getSortedNumbers();
            List<String> pageInfos = cgs.getPageInfosForCitations(cg);
            citMarkers.put(cgid,
                           style.getNumCitationMarker(numbers,
                                                      minGroupingCount,
                                                      pageInfos));
        }

        return citMarkers;
    }

    /**
     * Produce citation markers for the case of numbered citations
     * when the bibliography is not sorted by position.
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForIsNumberEntriesNotSortByPosition(CitationGroups cgs,
                                                              OOBibStyle style) {
        assert style.isNumberEntries();
        assert !style.isSortByPosition();

        cgs.createNumberedBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Integer> numbers = cg.getSortedNumbers();
            List<String> pageInfos = cgs.getPageInfosForCitations(cg);
            citMarkers.put(cgid,
                           style.getNumCitationMarker(numbers,
                                                      minGroupingCount,
                                                      pageInfos));
        }
        return citMarkers;
    }

    /**
     * Produce citMarkers for normal
     * (!isCitationKeyCiteMarkers &amp;&amp; !isNumberEntries) styles.
     *
     * @param cgs
     * @param style              Bibliography style.
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForNormalStyle(CitationGroups cgs,
                                         OOBibStyle style) {

        assert !style.isCitationKeyCiteMarkers();
        assert !style.isNumberEntries();
        // Citations in (Au1, Au2 2000) form

        CitedKeys sortedCitedKeys = cgs.getCitedKeysSortedInOrderOfAppearance();

        createNormalizedCitationMarkersForNormalStyle(sortedCitedKeys, style);
        createUniqueLetters(sortedCitedKeys, cgs); // calls distributeUniqueLetters(cgs)
        cgs.createPlainBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        // Finally, go through all citation markers, and update
        // those referring to entries in our current list:
        final int maxAuthorsFirst = style.getMaxAuthorsFirst();

        Set<String> seenBefore = new HashSet<>();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Citation> cits = cg.getSortedCitations();
            final int nCitedEntries = cits.size();
            List<String> pageInfosForCitations = cgs.getPageInfosForCitations(cg);

            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>(nCitedEntries);

            boolean hasUnresolved = false;
            for (int j = 0; j < nCitedEntries; j++) {
                Citation cit = cits.get(j);
                String currentKey = cit.citationKey;
                boolean isFirst = false;
                if (!seenBefore.contains(currentKey)) {
                    isFirst = true;
                    seenBefore.add(currentKey);
                }
                Optional<String> uniqueLetterForKey = cit.uniqueLetter;
                if (cit.db.isEmpty()) {
                    hasUnresolved = true;
                }
                Optional<BibDatabase> database = cit.db.map(e -> e.database);
                Optional<BibEntry> bibEntry = cit.db.map(e -> e.entry);

                CitationMarkerEntry cm =
                    new CitationMarkerEntryImpl(currentKey,
                                                bibEntry,
                                                database,
                                                uniqueLetterForKey,
                                                Optional.ofNullable(pageInfosForCitations.get(j)),
                                                isFirst);
                citationMarkerEntries.add(cm);
            }

            // TODO: Now we can pass CitationMarkerEntry values with unresolved
            //       keys to style.getCitationMarker,
            //       maybe the fall back to ungrouped citations here is
            //       not needed anymore.

            if (hasUnresolved) {
                /*
                 * Some entries are unresolved.
                 */
                String s = "";
                for (int j = 0; j < nCitedEntries; j++) {

                    CitationMarkerEntry cm = citationMarkerEntries.get(j);
                    if (cm.getBibEntry().isPresent()) {
                        s = (s
                             + style.getCitationMarker(citationMarkerEntries.subList(j, j + 1),
                                                       cg.itcType == OOProcess.AUTHORYEAR_PAR,
                                                       OOBibStyle.NonUniqueCitationMarker.THROWS));
                    } else {
                        s = s + String.format("(Unresolved(%s))", cm.getCitationKey());
                    }
                }
                citMarkers.put(cgid, s);
            } else {
                /*
                 * All entries are resolved.
                 */
                String citMarker = style.getCitationMarker(citationMarkerEntries,
                                                           cg.itcType == OOProcess.AUTHORYEAR_PAR,
                                                           OOBibStyle.NonUniqueCitationMarker.THROWS);
                citMarkers.put(cgid, citMarker);
            }
        }

        return citMarkers;
    }

    /**
     * The main field is citMarkers, the rest is for reuse in caller.
     */
    public static class ProduceCitationMarkersResult {

        public CitationGroups cgs;

        /** citation markers */
        public Map<CitationGroupID, String> citMarkers;

        ProduceCitationMarkersResult(CitationGroups cgs,
                                     Map<CitationGroupID, String> citMarkers) {
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

        Map<CitationGroupID, String> citMarkers;

        // fill citMarkers
        Map<String, String> uniqueLetters = new HashMap<>();

        if (style.isCitationKeyCiteMarkers()) {
            citMarkers = produceCitationMarkersForIsCitationKeyCiteMarkers(cgs, style);
        } else if (style.isNumberEntries()) {
            if (style.isSortByPosition()) {
                citMarkers = produceCitationMarkersForIsNumberEntriesIsSortByPosition(cgs, style);
            } else {
                citMarkers = produceCitationMarkersForIsNumberEntriesNotSortByPosition(cgs, style);
            }
        } else {
            /* Normal case, (!isCitationKeyCiteMarkers && !isNumberEntries) */
            citMarkers = produceCitationMarkersForNormalStyle(cgs, style);
        }

        return new ProduceCitationMarkersResult(cgs, /* has bibliography as a side effect */
                                                citMarkers);
    }

}
