package org.jabref.logic.openoffice.style;

import org.jabref.model.openoffice.ootext.OOText;
import org.jabref.model.openoffice.style.Citation;
import org.jabref.model.openoffice.style.CitationGroup;
import org.jabref.model.openoffice.style.CitationGroups;
import org.jabref.model.openoffice.util.OOListUtil;

import java.util.Optional;

class OOProcessCitationKeyMarkers {

    private OOProcessCitationKeyMarkers() {}

    /**
     *  Produce citation markers for the case when the citation
     *  markers are the citation keys themselves, separated by commas.
     */
    static void produceCitationMarkers(CitationGroups citationGroups, JStyle style) {
        assert style.isCitationKeyCiteMarkers();

        citationGroups.createPlainBibliographySortedByComparator(
                OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        for (CitationGroup group : citationGroups.getCitationGroupsInGlobalOrder()) {
            String citMarker =
                    style.getCitationGroupMarkupBefore()
                            + String.join(
                                    ",",
                                    OOListUtil.map(
                                            group.getCitationsInLocalOrder(),
                                            Citation::getCitationKey))
                            + style.getCitationGroupMarkupAfter();
            group.setCitationMarker(Optional.of(OOText.fromString(citMarker)));
        }
    }
}
