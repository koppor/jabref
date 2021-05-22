package org.jabref.logic.oostyle;

import java.util.Optional;

import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.ListUtil;
import org.jabref.model.oostyle.OOText;

class OOProcessCitationKeyMarkers {
    /**
     *  Produce citation markers for the case when the citation
     *  markers are the citation keys themselves, separated by commas.
     */
    static void produceCitationMarkers(CitationGroups cgs, OOBibStyle style) {

        assert style.isCitationKeyCiteMarkers();

        cgs.createPlainBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            String citMarker =
                style.getCitationGroupMarkupBefore()
                + String.join(",", ListUtil.map(cg.getCitationsInLocalOrder(), Citation::getCitationKey))
                + style.getCitationGroupMarkupAfter();
            cg.setCitationMarker(Optional.of(OOText.fromString(citMarker)));
        }
    }
}
