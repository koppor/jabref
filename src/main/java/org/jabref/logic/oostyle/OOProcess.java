package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jabref.logic.bibtex.comparator.FieldComparator;
import org.jabref.logic.bibtex.comparator.FieldComparatorStack;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.CitationMarkerEntry;
import org.jabref.model.oostyle.CitedKey;
import org.jabref.model.oostyle.CitedKeys;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.ListUtil;
import org.jabref.model.oostyle.NonUniqueCitationMarker;
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

    /* ***************************************
     *
     *     Make them unique: uniqueLetters or numbers
     *
     * **************************************/

    /**
     *  Fills {@code sortedCitedKeys//normCitMarker}
     */
    private static void createNormalizedCitationMarkersForNormalStyle(CitedKeys sortedCitedKeys,
                                                                      OOBibStyle style) {

        for (CitedKey ck : sortedCitedKeys.values()) {
            ck.normCitMarker = Optional.of(style.getNormalizedCitationMarker(ck));
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
    private static void createUniqueLetters(CitedKeys sortedCitedKeys, CitationGroups cgs) {

        // The entries in the clashingKeys lists preserve
        // firstAppearance order from sortedCitedKeys.values().
        //
        // The index of the citationKey in this order will decide
        // which unique letter it receives.
        //
        Map<String, List<String>> normCitMarkerToClachingKeys = new HashMap<>();
        for (CitedKey citedKey : sortedCitedKeys.values()) {
            String normCitMarker = OOFormattedText.toString(citedKey.normCitMarker.get());
            String citationKey = citedKey.citationKey;

            if (!normCitMarkerToClachingKeys.containsKey(normCitMarker)) {
                // Found new normCitMarker
                List<String> clashingKeys = new ArrayList<>(1);
                normCitMarkerToClachingKeys.put(normCitMarker, clashingKeys);
                clashingKeys.add(citationKey);
            } else {
                List<String> clashingKeys = normCitMarkerToClachingKeys.get(normCitMarker);
                if (!clashingKeys.contains(citationKey)) {
                    // First appearance of citationKey, add to list.
                    clashingKeys.add(citationKey);
                }
            }
        }

        // Clear old uniqueLetter values.
        for (CitedKey citedKey : sortedCitedKeys.values()) {
            citedKey.uniqueLetter = Optional.empty();
        }

        // For sets of citation keys figthing for a normCitMarker
        // add unique letters to the year.
        for (List<String> clashingKeys : normCitMarkerToClachingKeys.values()) {
            if (clashingKeys.size() <= 1) {
                continue; // No fight, no letters.
            }
            // Multiple citation keys: they get their letters
            // according to their order in clashingKeys.
            int nextUniqueLetter = 'a';
            for (String citationKey : clashingKeys) {
                String ul = String.valueOf((char) nextUniqueLetter);
                sortedCitedKeys.get(citationKey).uniqueLetter = Optional.of(ul);
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
     * Set isFirstAppearanceOfSource in each citation.
     *
     * Preconditions: globalOrder, localOrder
     */
    private static void setIsFirstAppearanceOfSourceInCitations(CitationGroups cgs) {
        Set<String> seenBefore = new HashSet<>();
        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            for (Citation cit : cg.getCitationsInLocalOrder()) {
                String currentKey = cit.citationKey;
                if (!seenBefore.contains(currentKey)) {
                    cit.setIsFirstAppearanceOfSource(true);
                    seenBefore.add(currentKey);
                } else {
                    cit.setIsFirstAppearanceOfSource(false);
                }
            }
        }
    }

    /**
     * Produce citMarkers for normal
     * (!isCitationKeyCiteMarkers &amp;&amp; !isNumberEntries) styles.
     *
     * @param cgs
     * @param style              Bibliography style.
     */
    private static Map<CitationGroupID, OOFormattedText>
    produceCitationMarkersForNormalStyle(CitationGroups cgs, OOBibStyle style) {

        assert !style.isCitationKeyCiteMarkers();
        assert !style.isNumberEntries();
        // Citations in (Au1, Au2 2000) form

        CitedKeys citedKeys = cgs.getCitedKeysSortedInOrderOfAppearance();

        createNormalizedCitationMarkersForNormalStyle(citedKeys, style);
        createUniqueLetters(citedKeys, cgs);
        cgs.createPlainBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        // Mark first appearance of each citationKey
        setIsFirstAppearanceOfSourceInCitations(cgs);

        Map<CitationGroupID, OOFormattedText> citMarkers = new HashMap<>();

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {

            final boolean inParenthesis = (cg.citationType == InTextCitationType.AUTHORYEAR_PAR);
            final NonUniqueCitationMarker strictlyUnique = NonUniqueCitationMarker.THROWS;

            List<Citation> cits = cg.getCitationsInLocalOrder();
            List<CitationMarkerEntry> citationMarkerEntries = ListUtil.map(cits, e -> e);
            OOFormattedText citMarker = style.getCitationMarker2(citationMarkerEntries,
                                                                 inParenthesis,
                                                                 strictlyUnique);
            citMarkers.put(cg.cgid, citMarker);
        }

        return citMarkers;
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
            citMarkers = produceCitationMarkersForNormalStyle(cgs, style);
        }

        return new ProduceCitationMarkersResult(cgs, /* has bibliography as a side effect */
                                                citMarkers);
    }

}
