package org.jabref.logic.oostyle;

import java.util.HashMap;
import java.util.Map;

import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.ListUtil;
import org.jabref.model.oostyle.OOFormattedText;

class OOProcessCitationKeyMarkers {
    /**
     *  Produce citation markers for the case when the citation
     *  markers are the citation keys themselves, separated by commas.
     */
    static Map<CitationGroupID, OOFormattedText>
    produceCitationMarkers(CitationGroups cgs, OOBibStyle style) {

        assert style.isCitationKeyCiteMarkers();

        cgs.createPlainBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        Map<CitationGroupID, OOFormattedText> citMarkers = new HashMap<>();

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            String citMarker =
                style.getCitationGroupMarkupBefore()
                + String.join(",", ListUtil.map(cg.getCitationsInLocalOrder(), Citation::getCitationKey))
                + style.getCitationGroupMarkupAfter();
            citMarkers.put(cg.cgid, OOFormattedText.fromString(citMarker));
        }
        return citMarkers;
    }
}
