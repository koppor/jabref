package org.jabref.logic.oostyle;

import java.util.List;
import java.util.Optional;

import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.CitationMarkerNumericEntry;
import org.jabref.model.oostyle.OOListUtil;
import org.jabref.model.oostyle.OOText;

class OOProcessNumericMarkers {

    /**
     * Produce citation markers for the case of numbered citations
     * with bibliography sorted by first appearance in the text.
     *
     * Numbered citation markers for each CitationGroup.
     * Numbering is according to first appearance.
     * Assumes global order and local order are already applied.
     *
     * @param cgs
     * @param style
     *
     */
    static void produceCitationMarkers(CitationGroups cgs, OOBibStyle style) {

        assert style.isNumberEntries();

        if (style.isSortByPosition()) {
            cgs.createNumberedBibliographySortedInOrderOfAppearance();
        } else {
            cgs.createNumberedBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);
        }

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            List<CitationMarkerNumericEntry> cits = OOListUtil.map(cg.getCitationsInLocalOrder(), e -> e);
            OOText citMarker = style.getNumCitationMarker2(cits);
            cg.setCitationMarker(Optional.of(citMarker));
        }
    }

}
